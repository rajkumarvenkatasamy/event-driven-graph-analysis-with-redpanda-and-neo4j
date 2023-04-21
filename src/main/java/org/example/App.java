package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
public class App {
    private static final String TOPIC = "movies-directed-by";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static void main(String[] args)  {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "test");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));

        try (Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"))) {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode jsonNode = objectMapper.readTree(record.value());
                    String movieId = jsonNode.get("movieId").asText();
                    String movieName = jsonNode.get("movieName").asText();
                    String directorName = jsonNode.get("directorName").asText();
                    int year = jsonNode.get("year").asInt();
                    createMovieDirectedBy(driver, movieId, movieName, directorName, year);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createMovieDirectedBy(Driver driver, String movieId, String movieName, String directorName, int year) {
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MERGE (m:Movie {id: $movieId, name: $movieName})\n" +
                                "MERGE (d:Director {name: $directorName})\n" +
                                "MERGE (d)-[:DIRECTED {year: $year}]->(m)\n",
                        ImmutableMap.of("movieId", movieId, "movieName", movieName, "directorName", directorName, "year", year));
                return null;
            });
        }

    }
}
