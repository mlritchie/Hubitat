def driverVersion() { return "1.0" }
/**
 *  Genmon Status Driver
 *
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

metadata {
	definition (name: "Genmon Status", namespace: "mlritchie", author: "ritchierich") {
		capability "Sensor"
        capability "Refresh"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "PowerSource"
    
        command "receiveData", [[name: "Receive Genmon Data", type: "STRING", description: ""]]
    
        attribute "Alarm Status", "string"
        attribute "Battery Voltage", "string"
        attribute "Engine State", "string"
        attribute "Last Alarm", "string"
        attribute "Last Run", "string"
        attribute "Last Outage", "string"
        attribute "Status", "string"
        attribute "Switch State", "string"
        attribute "Utility Max Voltage", "string"
        attribute "Utility Min Voltage", "string"
    }
    
    preferences {
		input name: "ipaddress", type: "text", title: "genmon Server IP", defaultValue: "0.0.0.0", required: true
        input name: "port", type: "text", title: "Connection Port", defaultValue: "8000", required: true
        input name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false
    }
}

def installed() {
    initialize()
}

def updated() {
	initialize()
}

def initialize() {
    logDebug("initialize")
}

def parse(String description) {

}

def on() {
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendEvent(name: "switch", value: "off")
}

def receiveData(description) {
    def result = []
    def logMsg = []
    logMsg.push("receiveData - description: ${description}")

    def bodyObject
    if (getObjectClassName(description) == "java.lang.String") {
        if (!description) return
        def jsonSlurper = new JsonSlurper()
        bodyObject = jsonSlurper.parseText(description)
    } else {
        bodyObject = description
    }
    def bodyKeys = bodyObject.keySet()

    for (int i = 0; i < bodyKeys.size(); i++) {
        Map evt = [isStateChange: false]
        def key = bodyKeys[i]
        evt.value = bodyObject[key]
        evt.name = key.substring(key.lastIndexOf("/")+1).trim()

        switch (evt.name) {
            case "Ambient Temperature Sensor":
                evt.name = "temperature"
                evt.unit = "F"
                evt.value = evt.value.replace(evt.unit, "").trim()
                break
            case "client_status":
                evt.name = "Status"
                break
            case "System In Outage":
                evt.name = "powerSource"
                evt.value = (evt.value == "No") ? "mains" : "dc"
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
        
        if (device.hasAttribute(evt.name)) {
            result << sendEvent(evt)
        } else {
            state[evt.name] = evt.value
        }
        logMsg.push("event ${evt}")
    }
    
    logDebug("${logMsg}")
    return result
}

def refresh() {
    if (!settings.ipaddress || !settings.port) {
        log.error "ipaddress and port are required to refresh data"
        return
    }
    
    def refreshParms = [
        uri: "http://${ipaddress}:${port}/cmd/status_json",
        requestContentType: 'application/json',
        contentType: 'application/json',
        timeout: 5
    ]
    try {
        httpGet(refreshParms) { resp ->
            def statusKeys = ["Engine","Line"]
            for (int st = 0; st < resp.data.Status.size(); st++) {
                def statusList = resp.data.Status[st]
                for (int k = 0; k < statusKeys.size(); k++) {
                    def statusKey = statusKeys[k]
                    if (statusList.containsKey(statusKey)) {
                        for (int i = 0; i < statusKeys.size(); i++) {
                            def statusItem = statusList[statusKey]
                            for (int j = 0; j < statusItem.size(); j++) {
                                //def key = statusItem[j].keySet()[0]
                                //def value = statusItem[j][key]
                                //log.trace "key: ${key}, value: ${value}"
                                receiveData(statusItem[j])
                            }
                        }
                    }
                }
            }
        }
    } catch (e) {
        log.error "refresh - Something went wrong: $e"
    }
}

private logDebug(msg) {
    if (isDebugEnabled != null && isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
