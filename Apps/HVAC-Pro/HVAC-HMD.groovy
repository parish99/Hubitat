/*  ----------------------------------------------------------------------------------
**  Copyright 2020 Jason Parish
**
**  Licensed under the Apache License, Version 2.0 (the "License");
**  you may not use this file except in compliance with the License.
**  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
**
**  Unless required by applicable law or agreed to in writing, software
**  distributed under the License is distributed on an "AS IS" BASIS,
**  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
**  See the License for the specific language governing permissions and
**  limitations under the License.
**
**  This device provides tile data for the HVAC-Pro application.
**
**  Version Notes // 06-13-20
**  1.0.5 Initial Release
**
**
** ---------------------------------------------------------------------------------*/

metadata {
    definition (name: "HVAC-HMD", namespace: "gunz", author: "Jason Parish") {
        //Capabilities 
        capability "SwitchLevel"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Sensor"
        capability "Thermostat"
            
        //Attributes
        attribute "targetTemp", 'Number'
        //attribute "humidity", 'Number'
        attribute "temperature", 'Number'
        //attribute "presence", 'enum', ['occupied','unoccupied']
        attribute "tMode", 'ENUM', ['auto', 'off', 'heat', 'emergency heat', 'cool']
        attribute "tState", 'ENUM', ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
        attribute "tileData", "string"
        attribute "message", "string"

    }
    
    preferences{
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def installed() {
    log.warn "installed..."
    setValues('Off','Idle',0,0,'Message')
    sendEvent(name: "tileData", value: "Waiting For Update")
}

def updated() {
    state.clear
    log.info "updated..."
    log.warn "description logging is: ${txtEnable == true}"
    installed()
}

def setValue(tm,ts,sp,pv,msg,cvsp,lpsm){
    sendEvent(name: "tMode", value: tm, unit: " Mode")
    sendEvent(name: "tState", value: ts, unit: " State")
    sendEvent(name: "targetTemp", value: sp, unit: "%")
    sendEvent(name: "temperature", value: pv, unit: "F")
    //sendEvent(name: "humidity", value: hl, unit: "%")
    //sendEvent(name: "presence", value: ps, unit: "State")
    sendEvent(name: "message", value: msg)
    
    def map=[:]
    map.tMode=tm
    map.tState=ts
    map.Target=sp
    map.Temperature=pv
    //map.Humidity=hl
    //map.Presence=os
    map.Msg=msg
    map.Vents=cvsp
    map.LoopSum=lpsm.toFloat().round(1)

    if (txtEnable) log.info "${map}"
    
    def tileDat = ""+"<br>"
    
    tileDat += device.label+"<br>"
    tileDat += "<br>"
    tileDat += "HVAC: "+map.tMode+" and "+map.tState+"<br>"
    //tileDat += "Temp SP: "+map.Target+"°F"+"<br>"
    tileDat += "Temp: (SP "+map.Target+"-PV "+map.Temperature+")°F"+"<br>"
    //tileDat += "Temp PV:    "+map.Temperature+"°F"+"<br>"
    //if (map.Humidity != null) tileDat += "Humidity:         "+map.Humidity+"%"+"<br>"
    //if (map.Presence != null) tileDat += "Presence:          "+map.Presence+"<br>"
    tileDat += "Status:      "+map.Msg+"<br>"
    map.Vents.each{k, v-> 
        if (txtEnable) log.info "${k}:${v}"
        tileDat += k+" : "+v+"%"+"<br>"
    }
    tileDat += "Open Vent Sum "+map.LoopSum+"<br>"
    
    if(map.Msg == "Unresponsive")tileDat = "<div style='color: black; height: 140%; background-color: #9900cc; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    //else if(map.Motion == "Active")tileDat = "<div style='color: black; height: 140%; background-color: #ffcc00; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    
    else if(map.tState == "Heating")tileDat = "<div style='color: white; height: 140%; background-color: #cc0000; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.tState == "Cooling")tileDat = "<div style='color: white; height: 140%; background-color: #0066cc; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.tMode == "Heat")tileDat = "<div style='color: white; height: 140%; background-color: #ff4d4d; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.tMode == "Cool")tileDat = "<div style='color: white; height: 140%; background-color: #33adff; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.tMode == "Auto")tileDat = "<div style='color: white; height: 140%; background-color: #00b300; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"

    else tileDat = "<div style='color: black; height: 140%; background-color: white; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    
    sendEvent(name: "tileData", value: tileDat, isStateChange: true, displayed: true)
}
