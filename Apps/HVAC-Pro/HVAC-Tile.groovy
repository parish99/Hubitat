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
        
/*        //Commands
        command "setTargetTemp", ["NUMBER"]
        command "setVentLevel", ["NUMBER"]
        command "setLightLevel", ["NUMBER"]
        command "setHumidLevel", ["NUMBER"]
        command "setTempLevel", ["NUMBER"]       
        command "setShadeLevel", ["NUMBER"]
        command "setMotion", ["ENUM"]
        command "setMode", ["STRING"]
        command "setFan", ["NUMBER"]
        command "refresh"
*/        
        //Attributes
        attribute "targetTemp", 'Number'
        attribute "ventPosition", 'Number'
        attribute "illuminance", 'Number'
        attribute "humidity", 'Number'
        attribute "temperature", 'Number'
        attribute "shadePosition", 'Number'
        attribute "presence", 'enum', ['Occupied','Unoccupied']
        attribute "mode", 'string'
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
    setValues("Occupied","Off",0,0,0,0,"Off",0,0,"Message")
    /*
    setTargetTemp(0)
    setVentLevel(0)
    setLightLevel(0)
    setHumidLevel(0.0)
    setTempLevel(0.0)
    setShadeLevel(0)
    setFanLevel(0)
    setMotion('Unoccupied')
    setMode('Inactive')
*/
    sendEvent(name: "tileData", value: "Waiting For Update")
    //sendEvent(name: "presence", value: 'Occupied')
}

def updated() {
    state.clear
    log.info "updated..."
    log.warn "description logging is: ${txtEnable == true}"
    installed()
}

//def parse(String description) {
//}


//////////////////////////////////////////////////////////////////////////////////
//(state.Motion,state.HVACmode,state.roomSetPoint,state.currentTemperature,state.currentHumidity,state.currentVentLevel,state.speed,state.currentShadeLevel,state.currentLux,Message)
def setValues(os,sm,tt,tl,hl,vl,sf,sl,ll,msg){
    sendEvent(name: "targetTemp", value: tt, unit: "%")
    sendEvent(name: "ventPosition", value: vl, unit: "%")
    sendEvent(name: "illuminance", value: ll, unit: "Lux")
    sendEvent(name: "humidity", value: hl, unit: "%")
    sendEvent(name: "temperature", value: tl, unit: "F")
    sendEvent(name: "shadePosition", value: sl, unit: "%")
    sendEvent(name: "presence", value: os, unit: "State")
    sendEvent(name: "mode", value: sm, unit: " Mode")
    sendEvent(name: "speed", value: sf, unit: "Speed")
    sendEvent(name: "message", value: msg)
    
    def map=[:]
    map.Presence=os
    map.Mode=sm.toLowerCase().capitalize()
    map.Target=tt
    map.Temperature=tl
    map.Humidity=hl
    map.VentPos=vl
    map.FanSpeed=sf
    map.ShadePos=sl
    map.Lux=ll
    map.Msg=msg

    if (txtEnable) log.info "${map}"
    
    def tileDat = ""+"<br>"
    if (map.Presence != null) tileDat += "Presence:          "+map.Presence+"<br>"
    tileDat += "HVAC:                     "+map.Mode+"<br>"
    tileDat += "Target Temp:      "+map.Target+"°F"+"<br>"
    tileDat += "Temperature:    "+map.Temperature+"°F"+"<br>"
    if (map.Humidity != null) tileDat += "Humidity:         "+map.Humidity+"%"+"<br>"
    tileDat += "Vent Position:     "+map.VentPos+"%"+"<br>"
    if (map.FanSpeed != null) tileDat += "Fan Speed:                "+map.FanSpeed+"<br>"
    tileDat += "Message:      "+map.Msg+"<br>"
    
    //tileDat = "<div style='color: black; height: 90%; background-color: green; font-size: 15px; white-space: pre-wrap; position: absolute; left: 0; top: 10%; width: 130%;'><div style='position: absolute;top: 50%;left: 35%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    
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



def updateDisplayTile(){
    sendEvent(name: "message", value: "Vent Online")
    def tileDat = ""+"<br>"
    tileDat += "Presence:          "+device.currentValue("presence")+"<br>"
    tileDat += "Mode:                     "+(device.currentValue("mode").toLowerCase().capitalize())+"<br>"
    tileDat += "Target Temp:      "+device.currentValue("targetTemp")+"°F"+"<br>"
    tileDat += "Temperature:    "+device.currentValue("temperature")+"°F"+"<br>"
    tileDat += "Humidity:         "+device.currentValue("humidity")+"%"+"<br>"
    tileDat += "Vent Position:     "+device.currentValue("ventPosition")+"%"+"<br>"
    tileDat += "Fan Speed:                "+device.currentValue("fanSpeed")+"<br>"
    tileDat += "Message:      "+device.currentValue("message")+"<br>"
    
    //tileDat = "<div style='color: black; height: 90%; background-color: green; font-size: 15px; white-space: pre-wrap; position: absolute; left: 0; top: 10%; width: 130%;'><div style='position: absolute;top: 50%;left: 35%;transform: translate(-50%, -50%);'${tileDat}</div></div>"
    
    sendEvent(name: "tileData", value: tileDat, isStateChange: true, displayed: true)

}
*/
