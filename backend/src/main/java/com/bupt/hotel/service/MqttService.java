package com.bupt.hotel.service;

import com.bupt.hotel.entity.Room;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class MqttService {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.topic.status.prefix}")
    private String statusTopicPrefix;

    private MqttClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(brokerUrl, clientId + "_" + System.currentTimeMillis(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            client.connect(options);
            log.info("Connected to MQTT Broker: {}", brokerUrl);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker", e);
        }
    }

    public void publishStatus(String roomId, Room room) {
        if (client == null || !client.isConnected())
            return;
        try {
            String topic = statusTopicPrefix + roomId + "/status";
            String payload = objectMapper.writeValueAsString(room);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(0);
            client.publish(topic, message);
        } catch (Exception e) {
            log.error("Error publishing status for room {}", roomId, e);
        }
    }
}
