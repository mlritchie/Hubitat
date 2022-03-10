def version() {"v1.1"}

/**
 *  Plivo Container
 *
 *  Copyright 2021 Michael Ritchie
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
	definition (name: "Plivo Container", namespace: "mlritchie", author: "Michael Ritchie") {
        attribute "containerSize", "number"	//stores the total number of child switches created by the container
        command "createDevice", ["DEVICE LABEL", "PHONE NUMBER"] //create any new Virtual Device
    }
}

preferences {
	input("authID", "text", title: "Plivo Auth ID:", description: "Plivo Auth ID")
  	input("authToken", "text", title: "Plivo Auth Token:", description: "Plivo Auth Token")
    input("useAlphaSender", "bool", title: "Use Alpha Sender?", defaultValue: false, required: false)
    def accountDetails = getValidated()
    if (accountDetails.validAccount) {
        if (useAlphaSender == true) {
		    input("fromNumber", "string", title: "Alpha Sender:", description: "Alpha Sender to use.", required: true)
        } else if (accountDetails.phoneList.size() > 0) {
            input("fromNumber", "enum", title: "Plivo Phone Number:", description: "Plivo phone number to use.", options: accountDetails.phoneList, required: true)
        }
    }
    input("voiceURL", "string", title: "Plivo PHLO URL", description: "To support voice calls, please setup a PHLO in the Plivo Console and paste in the URL here.", required: false)
	input("isDebugEnabled", "bool", title: "Enable debug logging?", defaultValue: false, required: false)
}

def createDevice(deviceLabel, devicePhoneNumber){
    try{
    	state.vsIndex = state.vsIndex + 1	//increment even on invalid device type
		def deviceID = deviceLabel.toString().trim().toLowerCase().replace(" ", "_")
		logDebug "Attempting to create Virtual Device: Label: ${deviceLabel}, Phone Number: ${devicePhoneNumber}"
		childDevice = addChildDevice("mlritchie", "Plivo Device", "${deviceID}-${state.vsIndex}", [label: "${deviceLabel}", isComponent: true])
    	logDebug "createDevice Success"
		childDevice.updateSetting("toNumber",[value:"${devicePhoneNumber}",type:"text"])
		logDebug "toNumber Update Success"
    	updateSize()
    } catch (Exception e) {
         log.warn "Unable to create device."
    }
}

def installed() {
	logDebug "Installing and configuring Virtual Container"
    state.vsIndex = 0 //stores an index value so that each newly created Virtual Switch has a unique name (simply incremements as each new device is added and attached as a suffix to DNI)
    initialize()
}

def updated() {
	initialize()
}

def initialize() {
	logDebug "Initializing Virtual Container"
	updateSize()
}

def uninstalled() {
    
}

def updateSize() {
	int mySize = getChildDevices().size()
    sendEvent(name:"containerSize", value: mySize)
}

def updatePhoneNumber() { // syncs device label with componentLabel data value
    def myChildren = getChildDevices()
    myChildren.each{
        if(it.label != it.data.label) {
            it.updateDataValue("toNumber", it.label)
        }
    }
}

def getValidated() {
	def answer = [:]
    answer.validAccount = false
    answer.phoneList = []
    
    def params = [
    	uri: "https://" + authID + ":" + authToken + "@api.plivo.com/v1/Account/" + authID + "/Number/"
  	]
    
    if ((authID =~ /[A-Za-z0-9]{20}/) && (authToken =~ /[A-Za-z0-9]{40}/)) {
        try {
        	httpGet(params){response ->
      			if (response.status != 200) {
        			log.error "Received HTTP error ${response.status}. Check your API Credentials!"
                } else {
                    logDebug "API credentials validated, Phone list generated"
                    answer.phoneList = response.data.objects.number
                    answer.validAccount = true
                }
    		}
        } catch (Exception e) {
        	log.error "getValidated: Invalid API Credentials were probably entered. Plivo Server Returned: ${e}"
		} 
    } else {
    	log.error "Account SID '${authID}' or Auth Token '${authToken}' is not properly formatted!"
  	}
    
    return answer
}

def sendNotification(toNumber, message, deviceID) {
    def toNumberList = toNumber.toString().split(",");     
    for (int i = 0; i < toNumberList.size(); i++) {
        toNumber = toNumberList[i].trim()
        if (!toNumber) continue
        
        def postBody = [
            src: "+${fromNumber}",
            dst: "${toNumber}",
            type: "sms",
            text: "${message}"
        ]

        def params = [
            uri: "https://" + authID + ":" + authToken + "@api.plivo.com/v1/Account/" + authID + "/Message/",
            contentType: "application/json",
            body: postBody
        ]

        if ((authID =~ /[A-Za-z0-9]{20}/) && (authToken =~ /[A-Za-z0-9]{40}/)) {
            try {
                httpPost(params){response ->
                    if (response.status != 202) {
                        log.error "Received HTTP error ${response.status}. Check your API Credentials!"
                    } else {
                        def childDevice = getChildDevice(deviceID)
                        if (childDevice) {
                            childDevice.sendEvent(name:"message", value: "${message}", displayed: false)
                        } else {
                            log.error "Could not find child device: ${deviceID}"
                        }
                        logDebug "Message Received by Plivo: ${message}"
                    }
                }
            } catch (Exception e) {
                log.error "deviceNotification: Invalid API Credentials were probably entered. Plivo Server Returned: ${e}"
            }
        } else {
            log.error "Account SID '${authID}' or Auth Token '${authToken}' is not properly formatted!"
        }
    }
}

def makeCall(toNumber, message, deviceID) {
    if (settings.voiceURL == null) {
        log.warn "The Plivo PHLO URL must be set in order to make calls."
        return
    }

    def toNumberList = toNumber.toString().split(",");     
    for (int i = 0; i < toNumberList.size(); i++) {
        toNumber = toNumberList[i].trim()
        if (!toNumber) continue

        def postBody = [
            from: "+${fromNumber}",
            to: "${toNumber}",
            message: message //URLEncoder.encode(message)
        ]

        def voiceURL = settings.voiceURL
        voiceURL = voiceURL.toString().replace("https://", "https://" + authID + ":" + authToken + "@")

        def params = [
            uri: voiceURL,
            contentType: "application/json",
            body: postBody
        ]
        
        if ((authID =~ /[A-Za-z0-9]{20}/) && (authToken =~ /[A-Za-z0-9]{40}/)) {
            try {
                httpPost(params){response ->
                    if (response.status != 200) {
                        log.error "Received HTTP error ${response.status}. Check your API Credentials!"
                    } else {
                        def childDevice = getChildDevice(deviceID)
                        if (childDevice) {
                            childDevice.sendEvent(name:"message", value: "${message}", displayed: false)
                        } else {
                            log.error "Could not find child device: ${deviceID}"
                        }
                        logDebug "Message Received by Plivo: ${message}"
                    }
                }
            } catch (Exception e) {
                log.error "deviceNotification: Invalid API Credentials were probably entered. Plivo Server Returned: ${e}"
            }
        } else {
            log.error "Account SID '${authID}' or Auth Token '${authToken}' is not properly formatted!"
        }
    }
}

private logDebug(msg) {
	if (isDebugEnabled) {
		log.debug "$msg"
	}
}
