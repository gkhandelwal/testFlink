

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.SocketClientSink;
import org.apache.flink.streaming.util.serialization.SerializationSchema;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.apache.flink.util.Collector;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;

import org.apache.log4j.net.SocketServer;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;


import org.apache.flink.streaming.connectors.twitter.TwitterSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.StringTokenizer;

import static org.apache.zookeeper.ZooDefs.OpCode.error;


public class WordCount {

	public static void main(String[] args){

        try {
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            //DataStream<Tuple2<String, Integer>> dataStream1 = env.socketTextStream("localhost", 9999).flatMap(new Splitter());
            //DataStream<String> dataStream2 = env.socketTextStream("localhost", 9000);
            DataStream<Tuple2<String, Integer>> dataStream = env
                    .socketTextStream("localhost", 9999)
                    .flatMap(new Splitter())
                    .keyBy(0)
                    .timeWindow(Time.seconds(5))
                    .sum(1);

            dataStream.addSink(new SocketClientSink<Tuple2<String, Integer>>("127.0.0.1", 9000, new SerializationSchema<Tuple2<String, Integer>>() {
                @Override
                public byte[] serialize(Tuple2<String, Integer> stringIntegerTuple2) {
                    String result = stringIntegerTuple2.f0 + "," + stringIntegerTuple2.f1 + "\n";
                    return result.getBytes();
                }
            }));

            //WriteToSocketUserClass.write("127.0.0.1",9000,"hell$1");



        /*dataStream.writeToSocket("127.0.0.1", 9000, new SerializationSchema<Tuple2<String, Integer>>() {
            @Override
            public byte[] serialize(Tuple2<String, Integer> stringIntegerTuple2) {
                String result = stringIntegerTuple2.f0 + "$" + stringIntegerTuple2.f1;
                System.out.println(result);
                return result.getBytes();
            }
        });*/

            env.execute("Window WordCount");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
	}

	public class WriteToSocketUserClass
    {
        public void write(String host, Integer port, String data) throws IOException {
            //Create and open socket
            Socket socket = new Socket(host, port);
            OutputStream out = socket.getOutputStream();
            if (data != null) {
                out.write(data.getBytes());
            }
            if(socket!=null)
                socket.close();
        }

    }
	public static class Splitter implements FlatMapFunction<String, Tuple2<String, Integer>> {
		/*@Override
		public void flatMap(String sentence, Collector<Tuple2<String, Integer>> out) throws Exception {
			for (String word: sentence.split(" ")) {
				out.collect(new Tuple2<String, Integer>(word, 1));
			}
		}*/

		// for central server
        @Override
		public void flatMap(String sentence, Collector<Tuple2<String, Integer>> out) throws Exception {
            /*if(sentence.contains("Hillary") || sentence.contains("hillary"))
                out.collect(new Tuple2<String, Integer>("Hillary", 1));
            if(sentence.contains("Donald") || sentence.contains("donald"))
                out.collect(new Tuple2<String, Integer>("Donald", 1));*/
            if(sentence.toLowerCase().matches("(?i).*hillary.*"))
                out.collect(new Tuple2<String, Integer>("Hillary", 1));
            if(sentence.toLowerCase().matches("(?i).*donald.*"))
                out.collect(new Tuple2<String, Integer>("Donald", 1));
            if(sentence.toLowerCase().matches("(?i).*vote.*"))
                out.collect(new Tuple2<String, Integer>("Vote", 1));
            out.collect(new Tuple2<String, Integer>("total", 1));
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
