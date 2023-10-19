/**
 *  Mode Device Manager v2.0
 *
 *  Credits:
 *  Originally posted on the Hubitat Community as 'Mode Switches' https://community.hubitat.com/t/released-control-switches-by-mode/121043
 *  Special thanks to Bruce Ravenel and Hubitat for posting the community version of this app for customers to learn from and augment to their needs
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

definition(
	name: "Mode Device Manager",
	namespace: "mlritchie",
	author: "Michael Ritchie",
	description: "Control Devices by Mode",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: ""
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	if(!state.modeSwitch) state.modeSwitch = [:]
    if(!state.modeLock) state.modeLock = [:]
	dynamicPage(name: "mainPage", title: "Mode Device Manager", uninstall: true, install: true) {
		section {
			input "lights", "capability.switch", title: "Select Switches to Control", multiple: true, submitOnChange: true, width: 4
            input "locks", "capability.lock", title: "Select Locks to Control", multiple: true, submitOnChange: true, width: 4
			if(lights) {
				lights.each{dev ->
					if(!state.modeSwitch[dev.id]) state.modeSwitch[dev.id] = [:]
					location.modes.each{if(!state.modeSwitch[dev.id]["$it.id"]) state.modeSwitch[dev.id]["$it.id"] = " "}
				}
				paragraph displaySwitchTable()
                if (state.newLevel) {
                    input name: "newLevel", type: "number", title: "Dim Level: (range 1-100)", range: "1..100", required: true, submitOnChange: true, width: 4, newLineAfter: true
                    if (newLevel) {
                        state.modeSwitch[state.newLevel[0]][state.newLevel[1]] = newLevel
                        state.remove("newLevel")
                        app.removeSetting("newLevel")
                        paragraph "<script>{changeSubmit(this)}</script>"
                    }
                }
			}
            if(locks) {
				locks.each{dev ->
					if(!state.modeLock[dev.id]) state.modeLock[dev.id] = [:]
					location.modes.each{if(!state.modeLock[dev.id]["$it.id"]) state.modeLock[dev.id]["$it.id"] = " "}
				}
				paragraph displayLockTable()
			}
            input "logging", "bool", title: "Enable Logging?", defaultValue: true, submitOnChange: true
		}
	}
}

String displaySwitchTable() {    
    String str = getTableHeader(2, "Switches")
	location.modes.each{str += "<th>On</th><th style='border-right:2px solid black' bgcolor='#F8F8F8'>Off</th>"}
	str += "</tr></thead>"
	String X = "<i class='he-checkbox-checked'></i>"
	String O = "<i class='he-checkbox-unchecked'></i>"
	lights.sort{it.displayName.toLowerCase()}.each {dev ->
        String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev<span style='color:black'>($dev.currentSwitch)</span>"
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>"
		location.modes.sort{it.name}.each{
            if (dev.hasCapability("SwitchLevel")) {
                String thisValue = state.modeSwitch["$dev.id"]["$it.id"]
                String var = thisValue.isNumber() ? buttonLink("$dev.id:$it.id:clear", thisValue, "purple") : buttonLink("$dev.id:$it.id:set", "Set", "green")
                str += "<td title='${thisValue.isNumber() ? "Unset thisValue" : "Set dimmer level"}'>$var</td>"
            } else {
                str += "<td>${buttonLink("$dev.id:$it.id:on", state.modeSwitch[dev.id]["$it.id"] == "on" ? X : O, "#1A77C9")}</td>"
            }
			str += "<td style='border-right:2px solid black' bgcolor='#F8F8F8'>${buttonLink("$dev.id:$it.id:off", state.modeSwitch[dev.id]["$it.id"] == "off" ? X : O, "#1A77C9")}</td>"
		}
	}
	str += "</tr></table></div>"
	str
}

String displayLockTable() {
    String str = getTableHeader(1, "Locks")
	location.modes.each{str += "<th style='border-right:2px solid black'>Lock</th>"}
	str += "</tr></thead>"
	String X = "<i class='he-checkbox-checked'></i>"
    String O = "<i class='he-checkbox-unchecked'></i>"
	locks.sort{it.displayName.toLowerCase()}.each {dev ->
		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev<span style='color:black'>($dev.currentLock)</span>"
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>"
		location.modes.sort{it.name}.each{
			str += "<td style='border-right:2px solid black'>${buttonLink("$dev.id:$it.id:lock", state.modeLock[dev.id]["$it.id"] == "lock" ? X : O, "#1A77C9")}</td>"
		}
	}
	str += "</tr></table></div>"
	str
}

def getTableHeader(columnNum, columnLabel) {
    String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'>Modes</th>"
	location.modes.sort{it.name}.each{str += "<th style='border-right:2px solid black' colspan='$columnNum'>${location.currentMode.id == it.id ? "<span style='color:BlueViolet'>$it.name</span" : "$it.name"}</th>"}
	str += "</tr><tr style='border-bottom:2px solid black'><td style='border-right:2px solid black'>$columnLabel</td>"
    
    return str
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
    List b = btn.tokenize(":")
    String s
    if (state.modeSwitch.containsKey(b[0])) {
        //s = state.modeSwitch[b[0]][b[1]]
        s = b[2]
        if (s == "set") {
            state.newLevel = b
        } else if (s == "clear") {
            state.modeSwitch[b[0]][b[1]] = " "
        } else {
            state.modeSwitch[b[0]][b[1]] = (["on","off"].indexOf(s) > -1 && state.modeSwitch[b[0]][b[1]] != s) ? s : " "
        }
    } else if (state.modeLock.containsKey(b[0])) {
        s = state.modeLock[b[0]][b[1]]
        state.modeLock[b[0]][b[1]] = s == " " ? b[2] : " "
    }
}

def updated() {
	unsubscribe()
	initialize()
}

def installed() {
	initialize()
}

void initialize() {
	subscribe(location, "mode", modeHandler)
    
    def settingsList = [
        lights: "modeSwitch",
        locks: "modeLock"
    ]
    def settingsListKeys = settingsList.keySet()
    for (int s = 0; s < settingsListKeys.size(); s++) {
        def settingItem = settingsListKeys[s]
        def stateItem = settingsList[settingItem]
        def deviceList = []
        for (int d = 0; d < settings[settingItem].size(); d++) {
            deviceList.push(settings[settingItem][d].id)
        }
        
        def stateItemKeys = state[stateItem].keySet()
        for (int v = 0; v < stateItemKeys.size(); v++) {
            def key = stateItemKeys[v]
            if (deviceList.indexOf(key) == -1) {
                state[stateItem].remove(key)
            }
        }
    }
}

void modeHandler(evt) {
	if (logging) log.info "Mode is now <b>$evt.value</b>"
	lights.each{dev -> 
		String s = state.modeSwitch[dev.id]["$location.currentMode.id"]
		if (s.isNumber()) {
            dev.setLevel(s.toInteger())
            if(logging) log.info "$dev was set to $s%"
        } else if (s != " ") {
			dev."$s"()
			if(logging) log.info "$dev turned $s"
		}
	}
    locks.each{dev -> 
		String s = state.modeLock[dev.id]["$location.currentMode.id"]
		if(s != " ") {
			dev."$s"()
			if(logging) log.info "$dev $s"
		}
	}
}
