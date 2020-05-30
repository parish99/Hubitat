definition(
    name: "SmartRoom",
    namespace: "Gunz",
    author: "Jason Parish",
    description: "Room application for 'SmartHVAC', do not install directly.",
    category: "My Apps",
    parent: "Gunz:SmartHVAC",
    iconUrl: "",
    iconX2Url: "",
	iconX3Url	: ""
   )

preferences {page(name: "pageConfig")} ////

def pageConfig(){
	dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) 
   {

      section("Room Name") {label title: "Enter a name for this room", required: false}    
        
      section("Pause") {input "PauseRoom", "bool", title: "Pause control for this room.", required: true, defaultValue: false, submitOnChange: true}
        
      section("Devices"){
         input "vStat", "capability.thermostat", title: "Zone Virtual Thermostat for setting zone setpoints", required: true
         input "vents", "capability.switchLevel", title: "Zone Vent",multiple: true, required: true
         input "tempSensor", "capability.temperatureMeasurement", title: "Zone Temperature Sensor", multiple: false, required: true
         input "humSensor", "capability.relativeHumidityMeasurement", title: "Zone Humidity Sensor", multiple: false, required: false
         input "luxSensor", "capability.illuminanceMeasurement", title: "Zone Light Sensor", multiple: false, required: false
         input "fan", "capability.fanControl", title: "Room Fan", multiple: false, required: false
         input "motion", "capability.motionSensor", title: "Room Motion Sensor", multiple: false, required: false
      }
      section("Settings"){
         input name: "Area", title: "Room Vent Size in²" ,multiple: false ,required: true ,type: "number" ,defaultValue: "38"      
         }

      section("Logging"){
          input(name: "logLevel" ,title: "IDE logging level" ,multiple: false ,required: true ,type: "enum" ,options: getLogLevels() ,submitOnChange: false ,defaultValue: "0")  
	  } 
      
      section("Room Tile"){
          input(name: "vRoom" ,title: "Create A Room Tile Device?" ,multiple: false ,required: false ,type: "bool" ,submitOnChange: true, defaultValue : false)  
	  } 

	}
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize(){
	infolog "Init"
	state.clear()
    state.area = Area 
    state.delta = 0.0
    state.ventCom="Online"
    debuglog "Subscribe to tempSensor"
    subscribe(tempSensor, "temperature", tempHandler)
    debuglog "Getting current Room Temperature" 
    state.currentTemperature = tempSensor.currentValue("temperature")  
    debuglog "Subscribe to vents"
    subscribe(vents, "level", ventHandler)
    debuglog "Getting All Vent Opening"
    state.VentOpeningMap = [:]
    vents.each{vent ->
		debuglog "Getting Vent ${vent}"
		state.VentOpeningMap[vent.displayName] =vent.currentValue("level")
    }   
    if(humSensor){
        debuglog "Subscribe to humidity"
        subscribe(humSensor, "humidity", humHandler)
        debuglog "Getting current Zone Humidity" 
        state.currentHumidity = humSensor.currentValue("humidity")  
    }
    if(motion){
        debuglog "Subscribe to motion"
        subscribe(motion, "motion.active", motionAHandler)
        debuglog "Getting current Zone Motion" 
        state.Motion = motion.currentValue("motion")
        state.occupied = motion.currentValue("motion")
    }
    if(luxSensor){
        debuglog "Subscribe to Light Sensor"
        subscribe(luxSensor, "motion.active", luxHandler)
        debuglog "Getting current room illumination" 
        state.currentLux = luxSensor.currentValue("illuminance")
    }
    if(fan){
        debuglog "Subscribe to Fan Status"
        subscribe(fan, "speed", fanHandler)
        debuglog "Getting current fan speed" 
        state.fanSpeed = speed.currentValue("speed")
    }
    debuglog "Getting House Thermostat State"
    state.HVACmode = parent.HVACmode() 
    debuglog "Subscribe to Room Thermostat"
    subscribe(vStat, "heatingSetpoint", setTstatHSP)
    subscribe(vStat, "coolingSetpoint", setTstatCSP)
    debuglog "Getting Room Thermostat Setpoint" 
	state.HeatSetpoint = vStat.currentValue("heatingSetpoint")
    state.CoolSetpoint = vStat.currentValue("coolingSetpoint")
    if (state.HVACmode=="HEAT") state.roomSetPoint = state.HeatSetpoint
    else state.roomSetPoint = state.CoolSetpoint 
    setDelta()
    updateMaster()
    if(vRoom) createRoomDevice()
	infolog "Done init"      
}


def createRoomDevice() {
    def roomDevice = getChildDevice("vRoom_${app.id}")
	if(!roomDevice) roomDevice = addChildDevice("Gunz", "SmartTile", "vRoom_${app.id}", null, [label: ("${app.label}"), name: thisName])
}

// EVENT HANDLERS
def tempHandler(evt) {
	infolog "Updated Temperature"
	state.currentTemperature = evt.value.toFloat().round(1)
	debuglog "Room Temperature set to ${state.currentTemperature}"
    setDelta()
	updateMaster()
}

def humHandler(evt) {
	infolog "Updated Humidity"
	state.currentHumidity = evt.value.toFloat().round(1)
	debuglog "Room Humidity set to ${state.currentHumidity}"
}

def motionAHandler(evt) {
	infolog "Motion Detected"
    state.occupied = evt.value
	state.Motion = evt.value
	debuglog "Room Motion set to ${state.Motion}"
	updateMaster()
}

def luxHandler(evt) {
	infolog "Light Level Changed"
	state.currentLux = evt.value
	debuglog "Room Lux set to ${state.currentLux}"
}

def fanHandler(evt) {
	infolog "Fan Speed Changed"
	state.fanSpeed = evt.value
	debuglog "Fan Speed set to ${state.fanSpeed}"
}

def ventHandler(evt) {
	infolog "Getting Vent Level for ${evt.device} value: ${evt.value}"
	infolog "state.VentOpeningMap before =  ${state.VentOpeningMap}"
	//state.VentOpeningMap = [:]
	vents.each{ vent ->
		debuglog "Getting Vent ${vent}"
		state.VentOpeningMap[vent.displayName] =vent.currentValue("level")
	}
	infolog "state.VentOpeningMap after =  ${state.VentOpeningMap}"
}

def setTstatHSP(evt) {
	infolog "Updated Room Heat Setpoint"
    state.HeatSetpoint = evt.value.toFloat()
    state.roomSetPoint = state.HeatSetpoint
	debuglog "Hot setpoint set to ${state.HeatSetpoint}"
    setDelta()
    updateMaster()
}

def setTstatCSP(evt) {
	infolog "Updated Cool Setpoint"
    state.CoolSetpoint = evt.value.toFloat()
    state.roomSetPoint = state.CoolSetpoint
	debuglog "Cold setpoint set to ${state.CoolSetpoint}"
    setDelta()
    updateMaster()
}

def setDelta(){
    if (state.HVACmode=="HEAT") state.delta = (state.HeatSetpoint - state.currentTemperature).toFloat().round(1)
    else state.delta = (state.currentTemperature - state.CoolSetpoint).toFloat().round(1)
    infolog "Updated Room Delta ${state.delta}"
}

// CALLED FROM PARENT
def getArea(){return(state.area)}
def getDelta(){return(state.delta)}
def MainTstatStateChange(Mode) {
    debuglog "Master Sent thrermostat mode ${Mode}"
    state.HVACmode=Mode
    vStat.setThermostatMode(Mode.toLowerCase())
    setDelta()
    updateMaster()
}
def setArea(newArea){
    state.ventSetPoint=(100/state.area*newArea).toInteger()
    infolog "New Setpoint From Master ${state.ventSetPoint}%"
    	vents.each{ vent ->  
            if(vent.currentValue("level")>(state.ventSetPoint+3)||vent.currentValue("level")<(state.ventSetPoint-3)){
                vent.setLevel(state.ventSetPoint)
                runIn(20,checkVentSP)
                infolog "Set Vent ${vent} to ${state.ventSetPoint}%"
            }
            else infolog "No Change To ${vent} ${vent.currentValue("level")}% is in Range"
        }
    if(vRoom) {
        def roomDevice = getChildDevice("vRoom_${app.id}")
        roomDevice.setVentLevel(state.ventSetPoint)
        infolog "Updated Child Device "
    }
}

def checkVentSP(){
    vents.each{ vent ->  
        if(vent.currentValue("level")>(state.ventSetPoint+3)||vent.currentValue("level")<(state.ventSetPoint-3)) state.ventCom="Offline"   
        else state.ventCom="Online"
        infolog "Vent ${vent}: is ${state.ventCom}"
    }
}

// SEND UPDATES TO PARENT 
def updateMaster(){
    infolog "Sending Room Values to Master"
    ChildMap = [:]
    ChildMap.room = app.label
    ChildMap.area = state.area
    ChildMap.delta = state.delta  
    ChildMap.setpoint = state.roomSetPoint
    ChildMap.currentTemperature = state.currentTemperature
    if(humSensor)ChildMap.currentHumidity = state.currentHumidity
    debuglog "Child Values ${ChildMap}"
    parent.SetChildStats(ChildMap)   
	infolog "Master was Updated"
}

// Debug/Logging
def debuglog(statement){   
    def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 2){log.debug(statement)}
}

def infolog(statement){       
    def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 1){log.info(statement)}
}

def getLogLevels() {
    return [["0":"None"],["1":"Info"],["2":"Debug"]]
}
