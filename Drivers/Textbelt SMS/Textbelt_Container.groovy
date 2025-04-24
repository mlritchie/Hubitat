def version() {"v1.0"}

/**
 *  Textbelt Container
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
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
	definition (name: "Textbelt Container", namespace: "mlritchie", author: "Michael Ritchie", importUrl: "") {
        capability "Notification"
        command "createDevice", ["DEVICE LABEL", "PHONE NUMBER"] //create any new Virtual Notification Device
    }
    
    attribute "quotaRemaining", "number" //stores the quota remaining

	preferences {
		input("apiKey", "text", title: "Textbelt API Key:", description: "Textbelt API Key")
		input("isDebugEnabled", "bool", title: "Enable debug logging?", defaultValue: false, required: false)
	}
}

def createDevice(deviceLabel, devicePhoneNumber){
    try{
        def deviceID = UUID.randomUUID().toString()
		logDebug("Attempting to create Virtual Device: Label: ${deviceLabel}, Phone Number: ${devicePhoneNumber}")
		childDevice = addChildDevice("mlritchie", "Textbelt Device", deviceID, [label: "${deviceLabel}", isComponent: false])
    	logDebug("createDevice Success")
		childDevice.updateSetting("toNumber",[value:"${devicePhoneNumber}",type:"text"])
		logDebug("toNumber Update Success")
    } catch (Exception e) {
         log.warn "Unable to create device."
    }
}

def installed() {
    initialize()
}

def updated() {
    state.remove("vsIndex")
    initialize()
}

def initialize() {
	logDebug("Initializing Virtual Container")
	checkQuota()
}

def uninstalled() {
    
}

def deviceNotification(message) {
    getChildDevices().each {
        it.deviceNotification(message)
    }
}

def sendNotification(toNumber, message, deviceID) {
    if (settings.apiKey) {
        def toNumberList = toNumber.toString().split(",");     
        for (int i = 0; i < toNumberList.size(); i++) {
            toNumber = toNumberList[i].trim()
            if (!toNumber) continue

            def postBody = [
                key: "${settings.apiKey}",
                phone: "${toNumber}",
                message: "${message}"
            ]

            def params = [
                uri: "https://textbelt.com/text",
                contentType: "application/json",
                body: postBody
            ]
            try {
                httpPost(params){response ->
                    if (response.status != 200) {
                        log.error "Received HTTP error ${response.status}. Check your API Key!"
                    } else {
                        def quotaRemaining = response.data.quotaRemaining
                        sendEvent(name: "quotaRemaining", value: "${quotaRemaining}")

                        if (response.data.success == false) {
                            log.error "Textbelt ${response.data.error}"
                        } else {
                            def childDevice = getChildDevice(deviceID)
                            if (childDevice) {
                                childDevice.sendEvent(name:"message", value: "${message}", descriptionText: "Sent to ${toNumber}, Text ID: ${response.data.textId}", displayed: false)
                            } else {
                                log.error "Could not find child device: ${deviceID}"
                            }
                            logDebug("Message Received by Textbelt: ${message}, textId: ${response.data.textId}")
                        }
                    }
                }
            } catch (Exception e) {
                log.error "deviceNotification: Textbelt Returned: ${e}"
            }
        }
    } else {
        log.error "Textbelt API key is not set, cannot send notifications."
    }
}

def checkQuota() {
        def params = [
            uri: "https://textbelt.com/quota/${settings.apiKey}",
            contentType: "application/json"
        ]

        if (settings.apiKey) {
            try {
                httpGet(params){response ->
                    if (response.status != 200) {
                        log.error "Received HTTP error ${response.status}. Check your API Key!"
                    } else {
                        log.trace "${response.data}"
                        def quotaRemaining = response.data.quotaRemaining
                        sendEvent(name: "quotaRemaining", value: "${quotaRemaining}")
                        
                        if (quotaRemaining < 1) {
                            log.error "Textbelt Out of quota"
                        }
                    }
                }
            } catch (Exception e) {
                log.error "deviceNotification: Textbelt Returned: ${e}"
            }
        } else {
            log.error "Textbelt API key is not set!"
        }
}

private logDebug(msg) {
	if (settings.isDebugEnabled) {
		log.debug "$msg"
	}
}
