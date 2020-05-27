metadata {
    definition (name: "Virtual Room", namespace: "Parish", author: "Jason Parish") {
        //Capabilities 
        capability "SwitchLevel"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Motion Sensor"
        capability "Sensor"
        capability "IlluminanceMeasurement"
        
        //Commands
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
        
        //Attributes
        attribute "targetTemp", 'Number'
        attribute "ventPosition", 'Number'
        attribute "illuminance", 'Number'
        attribute "humidity", 'Number'
        attribute "temperature", 'Number'
        attribute "shadePosition", 'Number'
        attribute "presence", 'enum', ['Occupied','Unoccupied']
        attribute "state", 'String'
        attribute "fanLevel", 'Number'

    }
    
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def installed() {
    log.warn "installed..."
    setTargetTemp(0)
    setVentLevel(0)
    setLightLevel(0)
    setHumidLevel(0.0)
    setTempLevel(0.0)
    setShadeLevel(0)
    setFanLevel(0)
    setMotion('Unoccupied')
    setMode('Active')
}

def updated() {
    log.info "updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

def parse(String description) {
}
//////////////////////////////////////////////////////////////////////////////////

def setTargetTemp(tt) {
    def descriptionText = "${device.displayName} Room Setpoint was set to $tt"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "targetTemp", value: tt, unit: "%", descriptionText: descriptionText)
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
    sendEvent(name: "state", value: sm, unit: "State", descriptionText: descriptionText)
}

def setFan(sf) {
    def descriptionText = "${device.displayName} Fan was set to $sf"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "fanLevel", value: sf, unit: "Level", descriptionText: descriptionText)
}
