/**
 *  Web Power Switch 7
 */
 
import java.util.List;
import groovy.transform.Field
@Field static Map getOutletNames = [
    "Switch 1 Name": 1
	,"Switch 2 Name": 2
	,"Switch 3 Name": 3
	,"Switch 4 Name": 4
	,"Switch 5 Name" : 5
    ,"Switch 6 Name": 6
	,"Switch 7 Name" : 7
	,"Switch 8 Name": 8
]

preferences {
        input("ip", "string", title:"IP Address", defaultValue: "192.168.0.100" ,required: true, displayDuringSetup: true)
        input("port", "string", title:"Port", defaultValue: "80" , required: true, displayDuringSetup: true)
        input("username", "string", title:"Username", defaultValue: "admin" , required: true, displayDuringSetup: true)
        input("password", "password", title:"Password", defaultValue: "password" , required: true, displayDuringSetup: true)
		input "isDebugEnabled", "bool", title: "Enable Debug Logging?", required: false
}

metadata {
	definition (name: "Web Power Switch", namespace: "mlritchie", author: "Michael Ritchie") {
		capability "Polling"
		capability "Refresh"
        capability "Switch"
		
		command "switchOff", [[name:"Switch Off", type: "ENUM", description: "Which port", constraints: getOutletNames.collect {k,v -> k}]]
		command "switchOn", [[name:"Switch On", type: "ENUM", description: "Which port", constraints: getOutletNames.collect {k,v -> k}]]
		command "switchCycle", [[name:"Switch Cycle", type: "ENUM", description: "Which port", constraints: getOutletNames.collect {k,v -> k}]]
        command "runScript", [[name:"Run Script",type:"NUMBER", description:"Line Number", constraints:["NUMBER"]]]
		
		attribute "Outlet1", "STRING"
		attribute "Outlet2", "STRING"
		attribute "Outlet3", "STRING"
		attribute "Outlet4", "STRING"
		attribute "Outlet5", "STRING"
		attribute "Outlet6", "STRING"
		attribute "Outlet7", "STRING"
		attribute "Outlet8", "STRING"
	}
}

def installed() {
    initialize()
}

def updated() {
 	initialize()
}

def initialize() {
    
}

// parse events into attributes
def parse(String description) {
    def map = [:]
    def descMap = parseDescriptionAsMap(description)
    def body = new String(descMap["body"].decodeBase64())
	def hexString = body.tokenize()
	logDebug "parse: ${hexString}"
    if (hexString[1].contains("status")) {
        def a = hexString[3].toString().replace('id="state">','');
        char[] charArray = a.toCharArray();
        def b = "${charArray[0]}${charArray[1]}"
        def binaryString = hexToBin(b)
        char[] StatusArray = binaryString.toString().toCharArray();
		
		for (int i = 1; i <= 8; i+=1) {
			def outletName = "Outlet" + i;
			def outletStatus = StatusArray[8-i] == "0" ? "off" : "on"
			logDebug "outletName: ${outletName}, outletStatus: ${outletStatus}, i: ${StatusArray[8-i]}"
			
			if (device.currentValue(outletName) != outletStatus) {
				sendEvent(name: outletName, value: outletStatus)
			}
			
		}
	} else {
        // Do nothing
		logDebug "not status"
	}
}

// handle commands
def on() {
    // do nothing
}

def off() {
    // do nothing
}

def poll() {
    getRemoteData()
}

def refresh() {
    getRemoteData()
}

def switchOff(outletName) {
	def outletNumber = getOutletNames[outletName]
    logDebug "switchOff - outletName: ${outletName}, outletNumber: ${outletNumber}"
    def command = "OFF"
	OutletAction(outletNumber, command)
}

def switchOn(outletName) {
	def outletNumber = getOutletNames[outletName]
    logDebug "switchOn - outletName: ${outletName}, outletNumber: ${outletNumber}"
    def command = "ON"
	OutletAction(outletNumber, command)
}

def switchCycle(outletName) {
	def outletNumber = getOutletNames[outletName]
    logDebug "switchCycle - outletName: ${outletName}, outletNumber: ${outletNumber}"
    def command = "CCL"
	OutletAction(outletNumber, command)
}

def runScript(lineNumber) {
    def lineString = lineNumber.toString()
    while (lineString.length() < 3) {
        lineString = "0" + lineString
    }
    logDebug "runScript - lineNumber: ${lineNumber}, lineString: ${lineString}"
	ScriptAction(lineString)
}

def OutletAction(outlet,action){
    def uri = "/outlet?${outlet}=${action}"
	logDebug "OutletAction - outlet: ${outlet}, action: ${action}, uri: ${uri}"
    delayBetween([	postAction(uri),
    				getRemoteData()
                 ], 5000);
}

def ScriptAction(lineNumber){
    //Example: http://192.168.0.100/script?run020=run
    def uri = "/script?run${lineNumber}=run"
	logDebug "ScriptAction - lineNumber: ${lineNumber}, uri: ${uri}"
    delayBetween([	postAction(uri),
    				getRemoteData()
                 ], 5000);
}

private getRemoteData() {
	def uri = "/status"
    postAction(uri)
}

// ------------------------------------------------------------------

private postAction(uri){
	setDeviceNetworkId(ip,port)  

	def userpass = encodeCredentials(username, password)
	def headers = getHeader(userpass)

	def hubAction = new hubitat.device.HubAction(
		method: "GET",
		path: uri,
		headers: headers
	)
	logDebug("Executing hubAction on " + getHostAddress())
	hubAction    
}

// ------------------------------------------------------------------
// Helper methods
// ------------------------------------------------------------------

def parseDescriptionAsMap(description) {
	description.split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

private encodeCredentials(username, password){
	def userpassascii = "${username}:${password}"
	def userpass = "Basic " + userpassascii.bytes.encodeBase64().toString()
    return userpass
}

private getHeader(userpass){
    def headers = [:]
    headers.put("HOST", getHostAddress())
    headers.put("Authorization", userpass)
    return headers
}

private setDeviceNetworkId(ip,port){
  	def iphex = convertIPtoHex(ip)
  	def porthex = convertPortToHex(port)
  	device.deviceNetworkId = "$iphex:$porthex"
  	logDebug "Device Network Id set to ${iphex}:${porthex}"
}

private getHostAddress() {
	return "${ip}:${port}"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex

}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}

private String hexToBin(String hex){
    String bin = "";
    String binFragment = "";
    int iHex;
    hex = hex.trim();
    hex = hex.replaceFirst("0x", "");

    for(int i = 0; i < hex.length(); i++){
        iHex = Integer.parseInt(""+hex.charAt(i),16);
        binFragment = Integer.toBinaryString(iHex);

        while(binFragment.length() < 4){
            binFragment = "0" + binFragment;
        }
        bin += binFragment;
    }
    return bin;
}

private logDebug(msg) {
	if (isDebugEnabled != false) {
		log.debug "$msg"
	}
}
