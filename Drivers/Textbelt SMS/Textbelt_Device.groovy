def version() {"1.0"}

/**
*	Textbelt Device
*
*  Copyright 2025 Michael Ritchie
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions an limitations under the License.
*
*
*/

metadata {
  	definition (name: "Textbelt Device", namespace: "mlritchie", author: "Michael Ritchie", importUrl: "https://raw.githubusercontent.com/mlritchie/Hubitat/refs/heads/master/Drivers/Textbelt%20SMS/Textbelt_Device.groovy") {
    	capability "Notification"
  	}
	
	attribute "lastMessage", "string"
    attribute "htmlLastMessage", "string"
}

preferences {
	input("toNumber", "text", title: "Phone Number:", description: "Phone number to send SMS to.", required: true)
    input("enableHTMLMessage", "bool", title: "Enable HTML Message Events?", defaultValue: false, required: false)
    input("enableNofications", "bool", title: "Enable Notifications?", defaultValue: true, required: false)
}

def installed() {
    initialize()
}

def updated() {
 	initialize()
}

def initialize() {
    state.version = version()
}

def deviceNotification(message) {
    if (settings.enableNofications == null || settings.enableNofications == true) {
        parent.sendNotification(toNumber, message, device.deviceNetworkId)
        sendEvent(name: "lastMessage", value: "${message}", descriptionText: "Sent to ${settings.toNumber}")
        createHTMLMsgEvent(message)
    } else {
        log.debug "Device ${settings.toNumber} disabled: ${message}"
    }
}

def createHTMLMsgEvent(message) {
    if (enableHTMLMessage == true) {
        sendEvent(name: "htmlLastMessage", value: "<div><h1 style='font-size:10px;'>${message}</h1></div>")
    }
}
