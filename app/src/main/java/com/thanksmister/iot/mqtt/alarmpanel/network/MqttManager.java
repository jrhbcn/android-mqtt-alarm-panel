package com.thanksmister.iot.mqtt.alarmpanel.network;

import android.content.Context;

import com.thanksmister.iot.mqtt.alarmpanel.utils.AlarmUtils;
import com.thanksmister.iot.mqtt.alarmpanel.utils.MqttUtils;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import timber.log.Timber;

public class MqttManager {

    final private MqttManagerListener listener;
    private MqttAndroidClient mqttAndroidClient;
    private boolean isConnected;
    
    public MqttManager(MqttManagerListener listener) {
        this.listener = listener;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface MqttManagerListener {
        void subscriptionMessage(String topic, String payload, String id);
        void handleMqttException(String errorMessage);
        void handleMqttDisconnected();
    }
    
    public void publishArmedHome() {
        String topic = AlarmUtils.COMMAND_TOPIC;
        String message = AlarmUtils.COMMAND_ARM_HOME;
        publishMessage(topic, message);
    }
    
    public void publishArmedAway() {
        String topic = AlarmUtils.COMMAND_TOPIC;
        String message = AlarmUtils.COMMAND_ARM_AWAY;
        publishMessage(topic, message);
    }
    
    public void publishDisarmed() {
        String topic = AlarmUtils.COMMAND_TOPIC;
        String message = AlarmUtils.COMMAND_DISARM;
        publishMessage(topic, message);
    }

    public void makeMqttConnection(final Context context, final boolean tlsConnection, final String broker, final int port,
                                    final String clientId, final String topic, final String username, final String password) {
       
        final String serverUri;
        // allow for cloud based MQTT brokers
        if(broker.contains("http://") || broker.contains("https://")) {
            serverUri = broker + ":" + String.valueOf(port);
        } else {
            if(tlsConnection) {
                serverUri = "ssl://" + broker + ":" + String.valueOf(port);
            } else {
                serverUri = "tcp://" + broker + ":" + String.valueOf(port);
            }
        }
   
        MqttConnectOptions mqttConnectOptions = MqttUtils.getMqttConnectOptions(username, password);
        mqttAndroidClient = MqttUtils.getMqttAndroidClient(context, serverUri, clientId, topic, new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Timber.d("Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic(topic);
                } else {
                    Timber.d("Connected to: " + serverURI);
                }
            }
            @Override
            public void connectionLost(Throwable cause) {
                Timber.d("The Connection was lost.");
            }
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Timber.i("Sent Message : " + topic + " : " + new String(message.getPayload()));
            }
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic(topic);
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Timber.e("Failed to connect to: " + serverUri + " exception: " + exception);
                    listener.handleMqttException("Failed to connect using the following broker and port: " + serverUri);
                }
            });
        } catch (MqttException e){
            e.printStackTrace();
            listener.handleMqttException(e.getMessage());
        }
    }

    private void subscribeToTopic(final String topic) {
        try {
            mqttAndroidClient.subscribe(topic, 0, new IMqttMessageListener() {
                @Override
                public void messageArrived(final String topic, final MqttMessage message) throws Exception {
                    Timber.i("Subscribe Message message : " + topic + " : " + new String(message.getPayload()));
                    listener.subscriptionMessage(topic, new String(message.getPayload()), String.valueOf(message.getId()));
                }
            });
        } catch (MqttException e){
            Timber.e("Exception while subscribing");
            e.printStackTrace();
            listener.handleMqttException(e.getMessage());
        }
    }

    public void publishMessage(String publishTopic, String publishMessage) {
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            Timber.d("Message Published: " + publishTopic);
            if(!mqttAndroidClient.isConnected()){
                listener.handleMqttDisconnected();
                /*showAlertDialog(getString(R.string.error_mqtt_connection), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        makeMqttConnection();
                    }
                });*/
                Timber.d("Unable to connect client.");
            }
        } catch (MqttException e) {
            Timber.e("Error Publishing: " + e.getMessage());
            e.printStackTrace();
            listener.handleMqttException(e.getMessage());
        }
    }
}