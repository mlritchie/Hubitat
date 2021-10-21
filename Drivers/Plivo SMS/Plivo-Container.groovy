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

def version() {"v1.0.20211021"}

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
    if (getValidated()) {
        if (useAlphaSender == true) {
		    input("fromNumber", "string", title: "Alpha Sender:", description: "Alpha Sender to use.", required: true)
        } else {
            input("fromNumber", "enum", title: "Plivo Phone Number:", description: "Plivo phone number to use.", options: getValidated("phoneList"), required: true)
        }
    }
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

def getValidated(type) {
	def validated = false
	
	if (type == "phoneList") {
		logDebug "Generating Plivo phone number list..."
	} else {
		logDebug "Validating API Credentials..."
	}
    
    def params = [
    	uri: "https://" + authID + ":" + authToken + "@api.plivo.com/v1/Account/" + authID + "/Number/"
  	]
    
    if ((authID =~ /[A-Za-z0-9]{20}/) && (authToken =~ /[A-Za-z0-9]{40}/)) {
        try {
        	httpGet(params){response ->
      			if (response.status != 200) {
        			log.error "Received HTTP error ${response.status}. Check your API Credentials!"
      			} else {
                    if (type=="phoneList") {
                        phoneList = response.data.objects.number
                        logDebug "Phone list generated phoneList: ${phoneList}"
                    } else {
                        logDebug "API credentials validated"
                        validated = true
                    }
      			}
    		}
        } catch (Exception e) {
        	log.error "getValidated: Invalid API Credentials were probably entered. Plivo Server Returned: ${e}"
		} 
    } else {
    	log.error "Account SID '${authID}' or Auth Token '${authToken}' is not properly formatted!"
  	}
	
    if (type == "phoneList") {
		return phoneList
	} else {
		return validated
	}    
}

def sendNotification(toNumber, message, deviceID) {
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

private logDebug(msg) {
	if (isDebugEnabled) {
		log.debug "$msg"
	}
}
