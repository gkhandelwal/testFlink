import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.StringTokenizer;

/**
 * Created by gaurav on 11/7/16.
 */
public class WordCombiner {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //DataStream<Tuple2<String, Integer>> dataStream1 = env.socketTextStream("localhost", 9999).flatMap(new Splitter());
        //DataStream<String> dataStream2 = env.socketTextStream("localhost", 9000);
        DataStream<Tuple2<String, Integer>> dataStream = env
                .socketTextStream("localhost", 9001)
                .flatMap(new Splitter())
                .keyBy(0)
                .timeWindow(Time.seconds(5))
                .sum(1);

        /*dataStream.writeToSocket("localhost", 9000, new SerializationSchema<Tuple2<String, Integer>>() {
            @Override
            public byte[] serialize(Tuple2<String, Integer> stringIntegerTuple2) {
                String result = stringIntegerTuple2.f0 + "$" + stringIntegerTuple2.f1;
                return result.getBytes();
            }
        });*/
        dataStream.print();

        env.execute("Window WordCombiner");
    }

    public static class Splitter implements FlatMapFunction<String, Tuple2<String, Integer>> {

        @Override
        public void flatMap(String sentence, Collector<Tuple2<String, Integer>> out) throws Exception
        {
            String result[] = sentence.split(",");
            try {
                if(result.length>=2)
                    out.collect(new Tuple2<String, Integer>(result[0], Integer.parseInt(result[1])));
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Deserialize JSON from twitter source
     *
     * <p>
     * Implements a string tokenizer that splits sentences into words as a
     * user-defined FlatMapFunction. The function takes a line (String) and
     * splits it into multiple pairs in the form of "(word,1)" ({@code Tuple2<String,
     * Integer>}).
     */
    public static class SelectEnglishAndTokenizeFlatMap implements FlatMapFunction<String, Tuple2<String, Integer>> {
        private static final long serialVersionUID = 1L;

        private transient ObjectMapper jsonParser;
        /**
         * Select the language from the incoming JSON text
         */
        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) throws Exception {
            if(jsonParser == null) {
                jsonParser = new ObjectMapper();
            }
            JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);
            boolean isEnglish = jsonNode.has("user") && jsonNode.get("user").has("lang") && jsonNode.get("user").get("lang").getTextValue().equals("en");
            boolean hasText = jsonNode.has("text");
            if (isEnglish && hasText) {
                // message of tweet
                StringTokenizer tokenizer = new StringTokenizer(jsonNode.get("text").getTextValue());

                // split the message
                while (tokenizer.hasMoreTokens()) {
                    String result = tokenizer.nextToken().replaceAll("\\s*", "").toLowerCase();

                    if (!result.equals("")) {
                        out.collect(new Tuple2<>(result, 1));
                    }
                }
            }
        }
    }

}
