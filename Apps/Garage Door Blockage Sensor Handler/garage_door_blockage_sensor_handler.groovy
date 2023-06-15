def appVersion() { return "1.2" }
/**
 *  Garage Door Blockage Sensor Handler
 *
 *  Copyright 2023 Michael Ritchie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
definition(
    name: "Garage Door Blockage Sensor Handler",
    namespace: "mlritchie",
    author: "Michael Ritchie",
    description: "Handle Garage Door Blockage Sensor Events",
    category: "Convenience",
    documentationLink: "https://community.hubitat.com/t/garage-door-blockage-indicator-system/110097/16",
    importUrl: "https://raw.githubusercontent.com/mlritchie/Hubitat/master/Apps/Garage%20Door%20Blockage%20Sensor%20Handler/garage_door_blockage_sensor_handler.groovy",
    iconUrl: "",
    iconX2Url: "",
    singleThreaded: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "${getFormat("title", "Garage Door Blockage Sensor Handler Version " + appVersion())}", uninstall: trie, install: true) {
        section(){
			if (!state.isPaused) {
				input(name: "pauseButton", type: "button", title: "Pause", backgroundColor: "Green", textColor: "white", submitOnChange: true)
			} else {
				input(name: "resumeButton", type: "button", title: "Resume", backgroundColor: "Crimson", textColor: "white", submitOnChange: true)
			}
		}
        section("${getFormat("box", "Garage Sensor Preferences")}") {
			input "contactSensor", "capability.contactSensor", title: "Garage Door Contact Sensor", multiple: false, required: true
        }
        section(){
            input "sensorLight", "capability.colorControl", title: "Garage Door Sensor RGB Indicator", multiple: false, required: true, width: 4
            input "timeOnThreshold", "number", title: "Number of minutes for RGB Indicator to stay on", required: false, defaultValue: 3, width: 4
        }
        section(){
            def doorSensorTypeLabel, doorSensorStateLabel
            if (settings.doorSensorType == null || settings.doorSensorType == true) {
                input "doorSensor", "capability.contactSensor", title: "Garage Door Beam Contact Sensor", width: 4, multiple: false, required: true
                doorSensorTypeLabel = "<b>Contact</b> or Motion?"
            } else {
                input "doorSensor", "capability.motionSensor", title: "Garage Door Beam Motion Sensor", width: 4, multiple: false, required: true
                doorSensorTypeLabel = "Contact or <b>Motion</b>?"
            }
            input "doorSensorType", "bool", title: "${doorSensorTypeLabel}", defaultValue: true, width: 4, submitOnChange: true
            if ((settings.doorSensorType == null || settings.doorSensorType == true) && (settings.doorSensorState == null || settings.doorSensorState == true)) {
                doorSensorStateLabel = "Indicator light <font color='red'><b>RED</b></font> when beam reports <b>Open</b> or Closed?"
            } else if ((settings.doorSensorType == null || settings.doorSensorType == true) && (settings.doorSensorState == false)) {
                doorSensorStateLabel = "Indicator light <font color='red'><b>RED</b></font> when beam reports Open or <b>Closed</b>?"
            } else if ((settings.doorSensorType == false) && (settings.doorSensorState == null || settings.doorSensorState == true)) {
                doorSensorStateLabel = "Indicator light <font color='red'><b>RED</b></font> when beam reports <b>Active</b> or Inactive?"
            } else if ((settings.doorSensorType == false) && (settings.doorSensorState == false)) {
                doorSensorStateLabel = "Indicator light <font color='red'><b>RED</b></font> when beam reports Active or <b>Inactive</b>?"
            }
            input "doorSensorState", "bool", title: "${doorSensorStateLabel}", defaultValue: true, width: 4, submitOnChange: true
        }
        section(){
			paragraph "${getFormat("text", "<b>Optional</b>: If the beam sensor is connected to a smart switch select that switch below and the beam sensor will be turned off when the garage door is closed and turned on when the garage door is open.")}"
            input "doorSensorPower", "capability.switch", title: "Garage Door Sensor Switch", multiple: false, required: false
            paragraph "${getFormat("line")}"
		}
        section("${getFormat("box", "Mode Settings")}") {
            input("modes", "mode", title: "Only activate when mode is", multiple: true, required: false)
            paragraph "${getFormat("line")}"
        }
		section("${getFormat("box", "Other preferences")}") {
			input "isDebugEnabled", "bool", title: "Enable Debugging?", defaultValue: false
            paragraph "${getFormat("line")}"
		}
    }
}
        

def installed() {
	state.isPaused = false
    initialize()
}

def updated() {
    unsubscribe()
	initialize()
}

def initialize() {    
    state.isScheduled = (state.isScheduled) ? state.isScheduled : false
    unschedule()
    
    if (!state.isPaused) {
        subscribe(contactSensor, "contact", garageDoorHandler)
        if (settings.doorSensorType == null || settings.doorSensorType == true) {
            subscribe(doorSensor, "contact", sensorContactHandler)
        } else {
            subscribe(doorSensor, "motion", sensorMotionHandler)
        }
    }
}

def garageDoorHandler(evt) {
    if (modes && !modes.contains(location.mode)) {
        return
    }
    
    def deviceName = evt.displayName
    def deviceValue = evt.value
    logDebug("garageDoorHandler - deviceName: ${deviceName}, deviceValue: ${deviceValue}")
    if (settings.doorSensorPower && deviceValue == "open") {
        doorSensorPower.on()
    } else {
        if (settings.doorSensorPower) doorSensorPower.off()
        sensorLight.off()
    }
}

def sensorContactHandler(evt) {
    if (modes && !modes.contains(location.mode)) {
        return
    }
    
    def logMsg = []
    def deviceName = evt.displayName
    def deviceValue = evt.value
    logMsg.push("sensorContactHandler - deviceName: ${deviceName}, deviceValue: ${deviceValue}")
    
    if ((deviceValue == "open" && settings.doorSensorState == true) || (deviceValue == "closed" && settings.doorSensorState == false)) {
        setColor("red")
        logMsg.push("Setting RGB indicator red")
    } else {
        setColor("green")
        logMsg.push("Setting RGB indicator green")
    }
    logDebug("${logMsg}")
}

def sensorMotionHandler(evt) {
    if (modes && !modes.contains(location.mode)) {
        return
    }
    
    def logMsg = []
    def deviceName = evt.displayName
    def deviceValue = evt.value
    logMsg.push("sensorMotionHandler - deviceName: ${deviceName}, deviceValue: ${deviceValue}")
    
    if ((deviceValue == "active" && settings.doorSensorState == true) || (deviceValue == "inactive" && settings.doorSensorState == false)) {
        setColor("red")
        logMsg.push("Setting RGB indicator red")
    } else {
        setColor("green")
        logMsg.push("Setting RGB indicator green")
    }
    logDebug("${logMsg}")
}

def setColor(color) {
    def hueValue
    switch(color) {
        case "red":
            hueValue = 100
            break
        case "green":
            hueValue = 33
            break
    }
    logDebug("setColor - hueValue: ${hueValue}")
    sensorLight.setColor(hue: hueValue, saturation: 100, level: 100)
    runIn(timeOnThreshold*60, "delayedOffHandler")
}

def delayedOffHandler() {
    sensorLight.off()
}

def appButtonHandler(btn) {
    switch(btn) {
        case "pauseButton":
			state.isPaused = true
            break
		case "resumeButton":
			state.isPaused = false
			break
    }
    updated()
}

def getFormat(type, displayText=""){ // Modified from @Stephack and @dman2306 Code   
    def color = "#1A77C9"
    if(type == "title") return "<h2 style='color:" + color + ";font-weight:bold'>${displayText}</h2>"
    if(type == "box") return "<div style='color:white;text-align:left;background-color:#1A77C9;padding:2px;padding-left:10px;'><h3><b><u>${displayText}</u></b></h3></div>"
    if(type == "text") return "<span style='font-size: 14pt;'>${displayText}</span>"
    if(type == "warning") return "<span style='font-size: 14pt;color:red'><strong>${displayText}</strong></span>"
    if(type == "line") return "<hr style='background-color:" + color + "; height: 1px; border: 0;'>"
    if(type == "code") return "<textarea rows=1 class='mdl-textfield' readonly='true'>${displayText}</textarea>"
}

private logDebug(msg) {
    if (isDebugEnabled != false) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
