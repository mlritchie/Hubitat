def driverVersion() { return "1.1" }
/**
 *  Genmon MQTT Status Driver
 *
 * Credits:
 * Thank you Kirk Rader for your MQTT Connection driver as I learned from it and leveraged some of your code within this driver.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

@Field static Map topicList = [
    "Genmon_Status":"Genmon/generator/client_status",
    "temperature":"Genmon/generator/Maintenance/Ambient Temperature Sensor",
    "Engine_State":"Genmon/generator/Status/Engine/Engine State",
    "Alarm_Log":"Genmon/generator/Status/Last Log Entries/Logs/Alarm Log",
    "Outage_Status":"Genmon/generator/Outage/Status",
    "powerSource":"Genmon/generator/Outage/System In Outage"
]

metadata {
	definition (name: "Genmon MQTT Status", namespace: "mlritchie", author: "ritchierich") {
		capability "Sensor"
        capability "Initialize"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "PowerSource"

        attribute "connection", "string" // State of the connection to the MQTT broker ("connected" or "disconnected").
        
        topicList.each { attributeName, topic ->
            if (["temperature", "powerSource"].indexOf(attributeName) == -1) attribute "${attributeName}", "string"
        }
        
        command "connect"
        command "disconnect"
        command "resetAlarm"
    }
    
    preferences {
        input name: "broker", type: "text", title: "Broker URL and Port", description: "use tcp:// or ssl:// prefix", required: true
        input name: "username", type: "text", title: "Username", description: "Broker username if required", required: false
        input name: "password", type: "password", title: "Password", description: "Broker userpassword if required", required: false
        input name: "ipaddress", type: "text", title: "genmon Server IP", defaultValue: "0.0.0.0", required: true
        input name: "port", type: "text", title: "Connection Port", defaultValue: "8000", required: true
        input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
    }
}

def installed() {
    sendEvent(name: "connection", value: "disconnected")
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    logDebug("initialize")
    if (settings.broker?.trim()) {
        disconnect()
        connect()
    }
}

def connect() {
    try {
        def mqttInt = interfaces.mqtt
        mqttInt.connect(settings.broker, "genmonHE-${device.id}", settings.username, settings.password)
        logDebug "connected to ${settings.broker}"
        
        if (mqttInt.isConnected()) {
            topicList.each { attributeName, topic ->
                logDebug "subscribed to ${topic}"
                mqttInt.subscribe(topic)
            }

            sendEvent(name: "connection", value: "connected")
        }
    } catch (e) {
        log.error "error connecting to MQTT broker: ${e}"
    }
}

def disconnect() {
    try {
        interfaces.mqtt.disconnect()
    } catch (e) {
        log.error "error disconnecting: ${e}"
    }

    logDebug "disconnected from ${settings.broker}"
    sendEvent(name: "connection", value: "disconnected")
}

def mqttClientStatus(String message) {
    if (message.startsWith("Error:")) {
        log.error "mqttClientStatus: ${message}"
        disconnect()
        runIn (5,"connect")
    } else {
        logDebug "mqttClientStatus: ${message}"
    }
}

def parse(String description) {
    def message = interfaces.mqtt.parseMessage(description)
    logDebug "parse ${message}"
    
    def evt = [:]
    evt.name = topicList.find{it.value == message.topic}.key
    evt.value = message.payload

    switch (evt.name) {
        case "temperature":
            evt.unit = "F"
            evt.value = evt.value.replace(evt.unit, "").trim()
            break
        case "powerSource":
            evt.value = (evt.value == "No") ? "mains" : "dc"
            break
        case "Engine State":
            def switchValue
            if (evt.value == "Cranking") {
                switchValue = "on"
            } else if (evt.value == "Off - Ready") {
                switchValue = "off"
            }
            if (switchValue) sendEvent(name: "switch", value: switchValue)
            break
        case "client_status":
            if (evt.value == "Offline") {
                //disconnect()
            }
            break
        case ~/(.*Voltage.*)/:
            evt.unit = "V"
            evt.value = evt.value.replace(evt.unit, "").trim()
            break
        case ~/(.*Frequency.*)/:
            evt.unit = "Hz"
            evt.value = evt.value.replace(evt.unit, "").trim()
            break
        case ~/(.*kW.*)/:
            evt.unit = "kW"
            evt.value = evt.value.replace(evt.unit, "").trim()
            break
    }
    log.trace "evt: ${evt}"
    sendEvent(evt)
}

def resetAlarm() {
    if (!settings.ipaddress || !settings.port) {
        log.error "ipaddress and port are required to reset alarm"
        return
    }
    
    def apiParams = [
        uri: "http://${ipaddress}:${port}",
        path: "/cmd/setremote",
        query: [setremote: "resetalarm"]
    ]
    
    try {
        httpGet(apiParams) {
            resp ->
            //apiResponse = resp.data
            logDebug "Resp Status: ${resp.status}"
        }
    } catch (e) {
        log.error "path: ${path}, error: ${e}"
    }
}

def on() {}

def off() {}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
