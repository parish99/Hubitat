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
**  Version Notes // 06-13-20
**  Multiple sections of code borrowed or inspired from other hubitat code,
**  but most significantly from Napalmcsr and the Keenectlite application.
**  1.0.0 Initial Release.
**  1.0.5 Added Home and Room Tile devices to coding.
**  1.0.6 Changed how the parent child updates work to fix ghost updates.
**  TTT
**
** ---------------------------------------------------------------------------------*/

definition(
    name: "HVAC-Room",
    namespace: "gunz",
    author: "Jason Parish",
    description: "Room application for 'HVAC-Pro', do not install directly.",
    category: "My Apps",
    parent: "gunz:HVAC-Pro",
    iconUrl: "",
    iconX2Url: "",
	iconX3Url	: ""
   )

preferences {page(name: "pageConfig")}

def pageConfig(){
	dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) 
   {

      section("Room Name") {label title: "Enter a name for this room", required: false}    
        
      section("Room Control"){
          input "PauseRoom", "bool", title: "Pause control.", required: false, defaultValue: false, submitOnChange: true ,width: 3
          input "infoEnable", "bool", title: "Enable Info Logging.", required: false, defaultValue: true, submitOnChange: true ,width: 3
          input "debugEnable", "bool", title: "Enable Debug Logging.", required: false, defaultValue: true, submitOnChange: true ,width: 3
      }
       
      section("Room Thermostat Control"){
          input "tstatMode", "bool", title: "Allow Main Thermostat to Control Room Thermostat Mode.", required: false, defaultValue: true, submitOnChange: true
          input "tstatTemp", "bool", title: "Allow Temperature Sensor To Update Room Thermostat Temperature.", required: false, defaultValue: true, submitOnChange: true
      }
        
      section("Devices"){
         input "vStat", "capability.thermostat", title: "Room Thermostat used for setpoints", required: true
         input "vents", "capability.switchLevel", title: "Room Vents",multiple: true, required: true
         input "tempSensor", "capability.temperatureMeasurement", title: "Room Temperature Sensor", multiple: false, required: true
         input "humSensor", "capability.relativeHumidityMeasurement", title: "Room Humidity Sensor", multiple: false, required: false
         input "luxSensor", "capability.illuminanceMeasurement", title: "Room Light Sensor", multiple: false, required: false
         input "fan", "capability.fanControl", title: "Room Fan", multiple: false, required: false
         input "motion", "capability.motionSensor", title: "Room Motion Sensor", multiple: false, required: false
      }
      section("Settings"){
         input name: "Area", title: "Room Vent Size in²" ,multiple: false ,required: true ,type: "number" ,defaultValue: "38"      
         }

      section("Room Tile"){input(name: "vRoom" ,title: "Create A Room Tile Device?" ,multiple:false ,required:false ,type: "bool" ,submitOnChange:true, defaultValue:false)} 

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
    state.msg = "Updating"
    state.delta = 0.0
    state.ventCom="Online"
    state.blower="Off"
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
        state.currentVentLevel =vent.currentValue("level")
    }
    state.ventSetPoint=state.currentVentLevel
    
    if(humSensor){
        debuglog "Subscribe to humidity"
        subscribe(humSensor, "humidity", humHandler)
        debuglog "Getting current Room Humidity" 
        state.currentHumidity = humSensor.currentValue("humidity")  
    }
    if(motion){
        debuglog "Subscribe to motion"
        subscribe(motion, "motion", motionAHandler)
        debuglog "Getting current Room Motion" 
        state.Motion = motion.currentValue("motion").capitalize()
        state.occupied = "Occupied" //motion.currentValue("motion")
    }
    if(luxSensor){
        debuglog "Subscribe to Light Sensor"
        subscribe(luxSensor, "illuminance", luxHandler)
        debuglog "Getting current room illumination" 
        state.currentLux = luxSensor.currentValue("illuminance")
    }
    if(fan){
        debuglog "Subscribe to Fan Status"
        subscribe(fan, "speed", fanHandler)
        debuglog "Getting current fan speed" 
        state.fanSpeed = speed.currentValue("speed")
    }   
    if (tstatMode){    
    debuglog "Getting House Thermostat Mode"
    vStat.setThermostatMode(parent.HVACmode().toLowerCase())  
    state.HVACmode = parent.HVACmode().toLowerCase().capitalize()
    debuglog "Getting House Thermostat State"        
    state.HVACstate = parent.HVACstate()       
    }   
    if (!tstatMode){    
    debuglog "Getting Room Thermostat Mode"    
    state.HVACmode = vStat.currentValue("thermostatMode") .toLowerCase().capitalize() 
    debuglog "Getting Room Thermostat State" 
    state.HVACstate = vStat.currentValue("thermostatOperatingState").toLowerCase().capitalize()    
    }
        
    debuglog "Subscribe to Room Thermostat"
    subscribe(vStat, "thermostatMode", setVstatMode)
    subscribe(vStat, "thermostatOperatingState", setVstatMode)
    subscribe(vStat, "heatingSetpoint", setTstatHSP)
    subscribe(vStat, "coolingSetpoint", setTstatCSP)

    
    debuglog "Getting Room Thermostat Setpoints" 
	state.HeatSetpoint = vStat.currentValue("heatingSetpoint")
    state.CoolSetpoint = vStat.currentValue("coolingSetpoint")
    if (state.HVACmode=="Heat") state.roomSetPoint = state.HeatSetpoint
    else state.roomSetPoint = state.CoolSetpoint 
    setDelta()
    updateMaster()
    if(vRoom)createRoomDevice()
    if(vRoom)childTileUpdate()
	infolog "Done init"      
}


def createRoomDevice() {
    def roomDevice = getChildDevice("vRoom_${app.id}")
	if(!roomDevice) roomDevice = addChildDevice("gunz", "HVAC-RMD", "vRoom_${app.id}", null, [label: ("${app.label}"), name: thisName])
}

// EVENT HANDLERS
def tempHandler(evt) {
	infolog "Updated Temperature"
	state.currentTemperature = evt.value.toFloat().round(1)
	debuglog "Room Temperature set to ${state.currentTemperature}"
    if(tstatTemp){vStat.setTemperature(state.currentTemperature)}
    setDelta()
	//updateMaster()
    if(vRoom)childTileUpdate()
}

def humHandler(evt) {
	infolog "Updated Humidity"
	state.currentHumidity = evt.value.toFloat().round(1)
	debuglog "Room Humidity set to ${state.currentHumidity}"
    if(vRoom)childTileUpdate()
}

def motionAHandler(evt) {
	infolog "Motion Detected"
    state.occupied = "Occupied"
	state.Motion = evt.value.capitalize()
	debuglog "Room Motion set to ${state.Motion}"
	//updateMaster()
    if(vRoom)childTileUpdate()
}

def luxHandler(evt) {
	infolog "Light Level Changed"
	state.currentLux = evt.value
	debuglog "Room Lux set to ${state.currentLux}"
    if(vRoom)childTileUpdate()
}

def fanHandler(evt) {
	infolog "Fan Speed Changed"
	state.fanSpeed = evt.value
	debuglog "Fan Speed set to ${state.fanSpeed}"
    if(vRoom)childTileUpdate()
}

def ventHandler(evt) {
	infolog "Getting Vent Level for ${evt.device} value: ${evt.value}"
	infolog "state.VentOpeningMap before =  ${state.VentOpeningMap}"
	//state.VentOpeningMap = [:]
	vents.each{ vent ->
		debuglog "Getting Vent ${vent}"
		state.VentOpeningMap[vent.displayName] =vent.currentValue("level")   
	}
    state.currentVentLevel = evt.value
    
    if(vRoom)childTileUpdate()
    
	infolog "state.VentOpeningMap after =  ${state.VentOpeningMap}"
}

def setTstatHSP(evt) {
	infolog "Updated Room Heat Setpoint"
    state.HeatSetpoint = evt.value.toFloat()
    state.roomSetPoint = state.HeatSetpoint
	debuglog "Hot setpoint set to ${state.HeatSetpoint}"
    setDelta()
    //updateMaster()
    if(vRoom)childTileUpdate()
}

def setTstatCSP(evt) {
	infolog "Updated Cool Setpoint"
    state.CoolSetpoint = evt.value.toFloat()
    state.roomSetPoint = state.CoolSetpoint
	debuglog "Cold setpoint set to ${state.CoolSetpoint}"
    setDelta()
    //updateMaster()
    if(vRoom)childTileUpdate()
}

def setVstatMode(evt) {
	infolog "Room Thermostat Mode"
	//state.HVACmode = evt.toLowerCase().capitalize() 
    state.HVACmode = vStat.currentValue("thermostatMode").toLowerCase().capitalize()    
    state.HVACstate = vStat.currentValue("thermostatOperatingState").toLowerCase().capitalize()      
       
    if (state.HVACmode=="Heat") state.roomSetPoint = state.HeatSetpoint
    else state.roomSetPoint = state.CoolSetpoint 
    //setDelta()
	debuglog "Room Thermostat set to ${state.HVACmode}"
    if(vRoom)childTileUpdate()
}
/*
def setVstatState(evt) {
	infolog "Set Room Thermostat State"
	state.HVACstate = evt.value.toLowerCase().capitalize() 
    state.HVACmode = vStat.currentValue("thermostatMode").toLowerCase().capitalize()
	debuglog "Room Thermostat set to ${state.HVACstate}"
    if(vRoom)childTileUpdate()
}
*/
def setDelta(){
    if (state.HVACmode=="Heat") state.delta = (state.HeatSetpoint - state.currentTemperature).toFloat().round(1)
    else state.delta = (state.currentTemperature - state.CoolSetpoint).toFloat().round(1)
    infolog "Updated Room Delta ${state.delta}"
    updateMaster()
}

// CALLED FROM PARENT
def getArea(){return(state.area)}
def getBlower(blower){
    debuglog "Master Sent Blower State ${blower}"
    state.blower=blower
    if (state.blower=="Closed"&&state.HVACmode=="Off") state.HVACstate="Fan On"
    if (state.blower=="Open"&&state.HVACmode=="Off") state.HVACstate="Idle"
    if(vRoom)childTileUpdate()
}
def getDelta(){return(state.delta)}

def MainTstatModeChange(Mode) {
    if(tstatMode){
        debuglog "Master Sent Thrermostat Update ${Mode}"   
        vStat.setThermostatMode(Mode.toLowerCase())
        //state.HVACmode=Mode.toLowerCase().capitalize()
        //setDelta()
        //updateMaster()
    }
}

def MainTstatStateChange(State) {
    if(tstatMode){
        //debuglog "Master Sent Thrermostat Update ${State}"
        //state.HVACstate=State.toLowerCase().capitalize()
        //if(vRoom)childTileUpdate()
    }
}

def setArea(newArea){
    if(!PauseRoom){
        state.ventSetPoint=(100/state.area*newArea).toInteger()
        infolog "New Setpoint From Master ${state.ventSetPoint}%"
    	vents.each{ vent ->  
            if(vent.currentValue("level")>=(state.ventSetPoint+3)||vent.currentValue("level")<=(state.ventSetPoint-3)){
                vent.setLevel(state.ventSetPoint)
                runIn(20,checkVentSP)
                infolog "Set Vent ${vent} to ${state.ventSetPoint}%"
            }
            else infolog "No Change To ${vent} ${vent.currentValue("level")}% is in Range"
        }
    
      if(vRoom)childTileUpdate() 
      
    }
    else infolog "Room is paused updates from parent were not applied."
}

def checkVentSP(){
    change=false
    comTemp=state.ventCom
    vents.each{ vent ->  
        if(vent.currentValue("level")>=(state.ventSetPoint+3)||vent.currentValue("level")<=(state.ventSetPoint-3))state.ventCom="Unresponsive"
        else state.ventCom="Online"
        infolog "Vent ${vent}: is ${state.ventCom}"
        if(state.ventCom!=comTemp)change=true
        if(change==true)runIn(30,tryAgain)
    }    
    if(vRoom && change)childTileUpdate()
}

def tryAgain(){
    vents.each{ vent ->  
            if(vent.currentValue("level")>=(state.ventSetPoint+3)||vent.currentValue("level")<=(state.ventSetPoint-3)){
                vent.setLevel(state.ventSetPoint)
                infolog "ReCheck Vent ${vent}: is ${state.ventCom}"
                runIn(30,checkVentSP)
            }
    }
}                                               
                                               
//**************************************************                                               
// SEND UPDATES TO PARENT 
/*
def updateMaster(){
    if(!PauseRoom){
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
        parent.childUpdateRequest(app.label)
    }
    else infolog "Room is paused no updates were sent to the parent."
}
*/

//*
def updateMaster(){
     if(!PauseRoom){parent.childUpdateRequest(app.label)}
      debuglog "Sent Update Request to Master"
}
//*/  

def getRoomData(){
        infolog "Sending Room Values to Master"
        ChildMap = [:]
        ChildMap.room = app.label
        ChildMap.area = state.area
        ChildMap.delta = state.delta  
        ChildMap.setpoint = state.roomSetPoint
        ChildMap.currentTemperature = state.currentTemperature
        if(humSensor)ChildMap.currentHumidity = state.currentHumidity
        debuglog "Child Values ${ChildMap}" 
	    infolog "Master was Updated"
        return(ChildMap)
}

// UPDATE CHILD TILE DEVICE
def childTileUpdate(){
    if(vRoom) {
        if (state.ventCom == "Unresponsive") state.msg = "Unresponsive"
        else state.msg = "Online"
        
        def roomDevice = getChildDevice("vRoom_${app.id}")
        roomDevice.setValues(state.occupied,state.Motion,state.HVACmode,state.HVACstate,state.roomSetPoint,state.currentTemperature,state.currentHumidity,state.ventSetPoint,state.currentVentLevel,state.fanSpeed,state.currentShadeLevel,state.currentLux,state.msg)
        infolog "Updated Child Device "
    }
}

// Debug/Logging
def infolog(statement){   
    if (infoEnable){log.info(statement)}
}
def debuglog(statement){   
    if (debugEnable){log.debug(statement)}
}
