package com.bupt.hotel.service;

import com.bupt.hotel.entity.FanSpeed;
import com.bupt.hotel.entity.Mode;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class MqttService {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.topic.status.prefix}")
    private String statusTopicPrefix;

    @Value("${mqtt.topic.command}")
    private String commandTopic;

    @Autowired
    @Lazy
    private SchedulerService schedulerService;

    @Autowired
    private RoomRepository roomRepository;

    private MqttClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class MqttCommand {
        private String roomId;
        private String type; // POWER_ON, POWER_OFF, CHANGE_STATE
        private Mode mode;
        private Double targetTemp;
        private FanSpeed fanSpeed;
    }

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(brokerUrl, clientId + "_" + System.currentTimeMillis(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(true);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT Connection Lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client.connect(options);
            client.subscribe(commandTopic, 1);
            log.info("Connected to MQTT Broker: {} and subscribed to {}", brokerUrl, commandTopic);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker", e);
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.info("Received MQTT message on {}: {}", topic, payload);
            MqttCommand cmd = objectMapper.readValue(payload, MqttCommand.class);

            if (cmd.getRoomId() == null)
                return;

            switch (cmd.getType()) {
                case "POWER_ON":
                    handlePowerOn(cmd);
                    break;
                case "POWER_OFF":
                    handlePowerOff(cmd);
                    break;
                case "CHANGE_STATE":
                    handleChangeState(cmd);
                    break;
                default:
                    log.warn("Unknown command type: {}", cmd.getType());
            }
        } catch (Exception e) {
            log.error("Error handling MQTT message", e);
        }
    }

    private void handlePowerOn(MqttCommand cmd) {
        Room room = roomRepository.findByRoomId(cmd.getRoomId()).orElse(null);
        if (room == null)
            return;

        room.setIsOn(true);
        // checkInTime is managed by Clerk
        // room.setCheckInTime(LocalDateTime.now());
        room.setTotalFee(0.0);
        roomRepository.save(room);

        schedulerService.requestSupply(cmd.getRoomId(), cmd.getMode(), cmd.getTargetTemp(), cmd.getFanSpeed());
    }

    private void handlePowerOff(MqttCommand cmd) {
        Room room = roomRepository.findByRoomId(cmd.getRoomId()).orElse(null);
        if (room == null)
            return;

        room.setIsOn(false);
        roomRepository.save(room);

        schedulerService.stopSupply(cmd.getRoomId(), true);
    }

    private void handleChangeState(MqttCommand cmd) {
        Room room = roomRepository.findByRoomId(cmd.getRoomId()).orElse(null);
        if (room == null || !room.getIsOn())
            return;

        schedulerService.requestSupply(cmd.getRoomId(), room.getMode(), cmd.getTargetTemp(), cmd.getFanSpeed());
    }

    public void publishStatus(String roomId, Room room) {
        // 异步执行，避免阻塞主线程导致卡顿
        CompletableFuture.runAsync(() -> {
            if (client == null || !client.isConnected())
                return;
            try {
                String topic = statusTopicPrefix + roomId + "/status";
                String payload = objectMapper.writeValueAsString(room);
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(0);
                client.publish(topic, message);
            } catch (Exception e) {
                log.debug("Error publishing status for room {}", roomId, e);
            }
        });
    }
}
