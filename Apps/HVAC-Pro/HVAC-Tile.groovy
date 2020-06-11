metadata {
    definition (name: "HVAC-Tile", namespace: "gunz", author: "Jason Parish") {
        //Capabilities 
        capability "SwitchLevel"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Motion Sensor"
        capability "Sensor"
        capability "IlluminanceMeasurement"
        capability "FanControl"
        capability "Thermostat"
            
        //Attributes
        attribute "targetTemp", 'Number'
        attribute "ventSetpoint", 'Number'
        attribute "ventPosition", 'Number'
        attribute "illuminance", 'Number'
        attribute "humidity", 'Number'
        attribute "temperature", 'Number'
        attribute "shadePosition", 'Number'
        attribute "presence", 'enum', ['occupied','unoccupied']
        attribute "motion", 'enum', ['active','inactive']
        attribute "tMode", 'ENUM', ['auto', 'off', 'heat', 'emergency heat', 'cool']
        attribute "tState", 'ENUM', ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
        attribute "speed", 'enum', ["low","medium-low","medium","medium-high","high","on","off","auto"]
        attribute "tileData", "string"
        attribute "message", "string"

    }
    
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def installed() {
    log.warn "installed..."
    setValues("Occupied","Inactive","Off","Idle",0,0,0,0,0,"Off",0,0,"Message")
    sendEvent(name: "tileData", value: "Waiting For Update")
}

def updated() {
    state.clear
    log.info "updated..."
    log.warn "description logging is: ${txtEnable == true}"
    installed()
}

//(state.occupied,state.Motion,state.HVACmode,state.HVACstate,state.roomSetPoint,state.currentTemperature,state.currentHumidity,state.ventSetPoint,state.currentVentLevel,state.fanSpeed,state.currentShadeLevel,state.currentLux,state.msg)

def setValues(os,mt,sm,ss,tt,tl,hl,vsp,vl,sf,sl,ll,msg){
    sendEvent(name: "targetTemp", value: tt, unit: "%")
    sendEvent(name: "ventPosition", value: vl, unit: "%")
    sendEvent(name: "ventSetpoint", value: vsp, unit: "%")
    sendEvent(name: "illuminance", value: ll, unit: "Lux")
    sendEvent(name: "humidity", value: hl, unit: "%")
    sendEvent(name: "temperature", value: tl, unit: "F")
    sendEvent(name: "shadePosition", value: sl, unit: "%")
    sendEvent(name: "presence", value: os, unit: "State")
    sendEvent(name: "motion", value: mt, unit: "State")
    sendEvent(name: "tMode", value: sm, unit: " Mode")
    sendEvent(name: "tState", value: ss, unit: " State")
    sendEvent(name: "speed", value: sf, unit: "Speed")
    sendEvent(name: "message", value: msg)
    
    def map=[:]
    if (txtEnable) log.info "${os},${mt},${sm},${ss}"
    map.Presence=os
    map.Motion=mt
    map.tMode=sm.toLowerCase().capitalize()
    map.tState=ss.toLowerCase().capitalize()
    map.Target=tt
    map.Temperature=tl
    map.Humidity=hl
    map.VentSP=vsp
    map.VentPos=vl
    map.FanSpeed=sf
    map.ShadePos=sl
    map.Lux=ll
    map.Msg=msg

    if (txtEnable) log.info "${map}"
    
    def tileDat = ""+"<br>"
    
    tileDat += device.label+"<br>"
    tileDat += "<br>"
    tileDat += "HVAC: "+map.tMode+" and "+map.tState+"<br>"
    //tileDat += "Temp SP: "+map.Target+"°F"+"<br>"
    tileDat += "Temp: (SP "+map.Target+"-PV "+map.Temperature+")°F"+"<br>"
    //tileDat += "Temp PV:    "+map.Temperature+"°F"+"<br>"
    if (map.Humidity != null) tileDat += "Humidity:         "+map.Humidity+"%"+"<br>"
    tileDat += "Vent: (SP "+map.VentSP+"-"+"PV "+map.VentPos+")%"+"<br>"
    if (map.FanSpeed != null) tileDat += "Fan Speed:                "+map.FanSpeed+"<br>"
    if (map.Lux != null) tileDat += "Light:                "+map.Lux+" lux"+"<br>"
    if (map.Presence != null) tileDat += "Presence:          "+map.Presence+"<br>"
    tileDat += "Status:      "+map.Msg+"<br>"
    
    //tileDat = "<div style='background-color: red;'${tileDat}</div>"
    if(map.Motion == "Active")tileDat = "<div style='color: black; height: 140%; background-color: #ffcc00; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.tMode == "Heat")tileDat = "<div style='color: white; height: 140%; background-color: #990000; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.tMode == "Cool")tileDat = "<div style='color: white; height: 140%; background-color: #0066cc; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else if(map.Msg == "Offline")tileDat = "<div style='color: black; height: 140%; background-color: #9900cc; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    else tileDat = "<div style='color: black; height: 140%; background-color: white; font-size: 18px; position: absolute; left: 0; top: -20%; width: 130%;'><div style='position: absolute;top: 45%;left: 39%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    
    sendEvent(name: "tileData", value: tileDat, isStateChange: true, displayed: true)
}


/*

def setTargetTemp(tt) {
    def descriptionText = "${device.displayName} Room Setpoint was set to $tt"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "targetTemp", value: tt, unit: "%", descriptionText: descriptionText)
    updateDisplayTile()
}

def setVentLevel(vl) {
    def descriptionText = "${device.displayName} Vent was set to $vl"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "ventPosition", value: vl, unit: "%", descriptionText: descriptionText)
}

def setLightLevel(ll) {
    def descriptionText = "${device.displayName} Light was set to $ll"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "illuminance", value: ll, unit: "Lux", descriptionText: descriptionText)
}

def setHumidLevel(hl) {
    def descriptionText = "${device.displayName} Humidity was set to $hl"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "humidity", value: hl, unit: "%", descriptionText: descriptionText)
}

def setTempLevel(tl) {
    def descriptionText = "${device.displayName} Light was set to $tl"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "temperature", value: tl, unit: "F", descriptionText: descriptionText)
}

def setShadeLevel(sl) {
    def descriptionText = "${device.displayName} Shade was set to $sl"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "shadePosition", value: sl, unit: "%", descriptionText: descriptionText)
}

def setMotion(os) {
    def descriptionText = "${device.displayName} Motion was set to $os"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "presence", value: os, unit: "State", descriptionText: descriptionText)
}

def setMode(sm) {
    def descriptionText = "${device.displayName} Mode was set to $sm"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "mode", value: sm, unit: " Mode", descriptionText: descriptionText)
}

def setFan(sf) {
    def descriptionText = "${device.displayName} Fan was set to $sf"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "fanSpeed", value: sf, unit: "Level", descriptionText: descriptionText)
}

*/
