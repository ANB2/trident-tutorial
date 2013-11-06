package tutorial.storm.trident.example;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.LocalDRPC;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import storm.kafka.KafkaConfig;
import storm.kafka.StringScheme;
import storm.kafka.trident.TransactionalTridentKafkaSpout;
import storm.kafka.trident.TridentKafkaConfig;
import storm.trident.TridentState;
import storm.trident.TridentTopology;
import storm.trident.operation.builtin.Count;
import storm.trident.operation.builtin.FirstN;
import storm.trident.operation.builtin.MapGet;
import storm.trident.operation.builtin.TupleCollectionGet;
import storm.trident.testing.FeederBatchSpout;
import storm.trident.testing.MemoryMapState;
import tutorial.storm.trident.operations.DebugFilter;
import tutorial.storm.trident.operations.ParseTweet;
import tutorial.storm.trident.operations.Split;
import tutorial.storm.trident.testutil.SampleTweet;

import java.io.IOException;

/**
 * @author Enno Shioji (enno.shioji@peerindex.com)
 */
public class Skeleton {

    public static StormTopology buildTopology(LocalDRPC drpc, FeederBatchSpout spout) throws IOException {

        TridentTopology topology = new TridentTopology();
        TridentState count =
        topology
                .newStream("tweets", spout)
                .each(new Fields("str"), new ParseTweet(), new Fields("text", "content", "user"))
                .project(new Fields("content", "user"))
                .each(new Fields("content"), new OnlyHashtags())
                .each(new Fields("user"), new OnlyEnglish())
                .each(new Fields("content", "user"), new Extract1(), new Fields("followerClass", "contentName"))
                .groupBy(new Fields("followerClass", "contentName"))
                .persistentAggregate(new MemoryMapState.Factory(), new Count(), new Fields("count"))
        ;

        count
                .newValuesStream()
                .each(new Fields("count"), new DebugFilter());


        topology
                .newDRPCStream("hashtag_count", drpc)
                .stateQuery(count, new TupleCollectionGet(), new Fields("followerClass", "contentName"))
                .stateQuery(count, new Fields("followerClass", "contentName"), new MapGet(), new Fields("count"))
                .groupBy(new Fields("followerClass"))
        ;

        return topology.build();
    }

    public static void main(String[] args) throws Exception {
        Preconditions.checkArgument(args.length == 1, "Please specify the test kafka broker host:port");
        String testKafkaBrokerHost = args[0];

//        TransactionalTridentKafkaSpout tweetSpout = tweetSpout(testKafkaBrokerHost);

        Config conf = new Config();

        LocalDRPC drpc = new LocalDRPC();
        LocalCluster cluster = new LocalCluster();
        FeederBatchSpout feederSpout = new FeederBatchSpout(ImmutableList.of("str"));

        SampleTweet sampleTweet = new SampleTweet();

        cluster.submitTopology("hackaton", conf, buildTopology(drpc,feederSpout));

//        spout.feed(new Values(ImmutableList.of("rose")));
//        spout.feed(new Values(ImmutableList.of("rose")));
//        spout.feed(new Values(ImmutableList.of("rose")));
//
//        spout.feed(new Values(ImmutableList.of("fred")));
//        spout.feed(new Values(ImmutableList.of("fred")));
//        spout.feed(new Values(ImmutableList.of("fred")));
//        spout.feed(new Values(ImmutableList.of("fred")));
//
//        spout.feed(new Values(ImmutableList.of("steve")));
//        spout.feed(new Values(ImmutableList.of("steve")));
//
//
        while(!Thread.currentThread().isInterrupted()){
            Thread.sleep(500);
            System.out.println(drpc.execute("hashtag_count",""));
            feederSpout.feed(ImmutableList.of(new Values(sampleTweet.sampleTweet())));
        }
    }

    private static TransactionalTridentKafkaSpout tweetSpout(String testKafkaBrokerHost) {
//        TweetIngestor ingestor = new TweetIngestor("/tmp/kafka", "test", 12000);
//        ingestor.startAndWait();
        KafkaConfig.BrokerHosts hosts = TridentKafkaConfig.StaticHosts.fromHostString(ImmutableList.of(testKafkaBrokerHost), 1);
        TridentKafkaConfig config = new TridentKafkaConfig(hosts, "test");
        config.scheme = new SchemeAsMultiScheme(new StringScheme());
        return new TransactionalTridentKafkaSpout(config);
    }

}
