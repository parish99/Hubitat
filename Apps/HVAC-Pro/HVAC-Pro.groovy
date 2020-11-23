/*  ----------------------------------------------------------------------------------
**  Copyright 2020 Jason Parish
**
**  This application is meant to control hvac smart vents to try and maintain a set
**  amount of open vents.  The purpose behind this method is to maintaine the optimal
**  amount of airflow across the HVAC equipment.  This can eliminate the need for a
**  dual zoned furnace for the upper and lower portion of a home.  It also allows
**  control over the temperature of the air coming out of the vents.
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
**
**
** ---------------------------------------------------------------------------------*/

 definition(
   name		: "HVAC-Pro",
   namespace	: "gunz",
   author	: "Jason Parish",
   description	: "Room Manager",
   category	: "My Apps",
   iconUrl	: "",
   iconX2Url	: "",
   iconX3Url	: ""
   )

preferences {page(name: "pageConfig")}

def pageConfig(){
    dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0){
       
      section("Settings"){
          input "Pause", "bool", title: "Pause Control.", required: false, defaultValue: false, submitOnChange: true ,width: 3
          input "infoEnable", "bool", title: "Enable Info Logging.", required: false, defaultValue: false, submitOnChange: true ,width: 3
          input "debugEnable", "bool", title: "Enable Debug Logging.", required: false, defaultValue: false, submitOnChange: true ,width: 3
      }
       
      section("HVAC Setup"){
         input name : "tStat", type : "capability.thermostat", title: "Main Household thermostat", required: true
         input name : "blowerRun", type : "capability.contactSensor", title: "Blower Running Input", required: false
         input name : "overPressure", type : "capability.contactSensor", title: "Static Pressure Overpressure Switch", required: false
         input "targetArea", "number", title: "Enter The Target Area in²:", defaultValue: 250, submitOnChange: true ,width: 4
         input "staticValue", "number", title: "Enter The Total in² of Static Vents:", defaultValue: 0, submitOnChange: true ,width: 4
         input "maxDelta", "number", title: "Enter The Max Room Delta (Degrees):", defaultValue: 3, submitOnChange: true ,width: 4
         input "refresh", "number", title: "Enter The Minimum Update Interval (Seconds):", defaultValue: 60, submitOnChange: true
      }
        
      section("Rooms"){
        if (installed){
            section("Rooms"){
                app(name: "childRooms", appName: "HVAC-Room", namespace: "gunz", title: "Create New Room...", multiple: true)
            }
        }	
      }
        
      section("HVAC Home Tile"){input(name: "HMDT" ,title: "Create A Home Tile Device?" ,multiple:false ,required:false ,type: "bool" ,submitOnChange:true, defaultValue:false)} 
        
	}
}

/* ---------------------------------------------------------------------------------*/
def installed(){
    initialize()
}
   
def updated(){
   unsubscribe()
   initialize()
}
   
def initialize(){
    unschedule()
    schedule("*/${refresh} * * ? * * *",update)
    state.clear()
    infolog "Initializing"
    subscribe(tStat, "thermostatMode", setTstatMode)
    debuglog "Getting local virtual Thermostat thermostatMode" 
	state.thermostatMode = tStat.currentValue("thermostatMode").toLowerCase().capitalize()
    debuglog "Main TstatState : ${state.thermostatMode}"    
    debuglog "Getting Thermostat running State" 
    subscribe(tStat, "thermostatOperatingState", OperatingStateHandler)  
    state.operState = tStat.currentValue("thermostatOperatingState").toLowerCase().capitalize()
    debuglog "Main TstatState : ${state.operState}"
    state.currentTemperature = tStat.currentValue("temperature")
    if(HMDT)subscribe(tStat, "heatingSetpoint", setTstatHSP)
    if(HMDT)subscribe(tStat, "coolingSetpoint", setTstatCSP)
    if(HMDT)subscribe(tStat, "temperature", tempHandler)

   if (blowerRun) {
   subscribe(blowerRun, "contact", blowerHandler)
   debuglog "Getting Blower State "
   state.blowerRun = blowerRun.currentValue("contact").toLowerCase().capitalize()
   childApps.each {child ->
       child.getBlower(state.blowerRun)
    }    
   }
   
   if (staticPres) {
   subscribe(staticPres, "contact", pressureHandler)
   debuglog "Getting Static Pressure State "
   state.overPressure = overPressure.currentValue("contact").toLowerCase().capitalize()
   }
   
   debuglog "There are ${childApps.size()} installed child apps"
   state.roomMap = [:]

   childApps.each {child ->
      debuglog "Child app: ${child.label}"
      state.roomMap[child.label]=[:]
      state.childVentSize= [:]
      state.childDelta= [:]
      state.childVentSP= [:]
      state.childVentPCT= [:]
   }  
    
   infolog "Getting Child Values"
   childApps.each {child ->
       state.roomMap[child.label].area=child.getArea()
       state.roomMap[child.label].delta=child.getDelta()
       state.childVentSize[child.label]=state.roomMap[child.label].area
       state.childDelta[child.label]=state.roomMap[child.label].delta
       debuglog "Initial Room Data:${[child.label]}:${state.roomMap[child.label]}"
   }   
    childVentCalc()   
    update()
    if(HMDT)createDataTile()
    
}
/* ---------------------------------------------------------------------------------*/

def createDataTile() {
    def roomDevice = getChildDevice("HMDT_${app.id}")
	if(!roomDevice) roomDevice = addChildDevice("gunz", "HVAC-HMD", "HMDT_${app.id}", null, [label: ("${app.label}"), name: thisName])
}

def setTstatMode(evt){
	infolog "Running setTstatMode"
	state.thermostatMode = evt.value.toLowerCase().capitalize()
    debuglog "Sending TStat Change to Zones ${state.thermostatMode}"
	childApps.each {child -> 
        child.MainTstatModeChange(state.thermostatMode)
    }
    if(HMDT)childTileUpdate()
}

def OperatingStateHandler(evt){
	debuglog "*********OperatingStateHandler event : ${evt.value}"
    state.operState = evt.value.toLowerCase().capitalize()
	//def newTstatState = evt.toLowerCase().capitalize()
    //if (newTstatState != state.operState) state.operState = newTstatState
    if(HMDT)childTileUpdate()
}

def blowerHandler(evt){
    debuglog "Blower Run event : ${evt}"
    state.blowerRun = evt.value.toLowerCase().capitalize()
    childApps.each {child ->
        child.getBlower(state.blowerRun)
    }
    if(HMDT)childTileUpdate()
    return state.blowerRun
}
   
def pressureHandler(evt){
    debuglog "Overpressure Run event : ${evt}"
    state.overPressure = evt.value.toLowerCase().capitalize()
    if(HMDT)childTileUpdate()
    return state.overPressure
}

def setTstatHSP(evt) {
	infolog "Updated Room Heat Setpoint"
    state.HeatSetpoint = evt.value.toFloat()
    state.roomSetPoint = state.HeatSetpoint
	debuglog "Hot setpoint set to ${state.HeatSetpoint}"
    childTileUpdate()
}

def setTstatCSP(evt) {
	infolog "Updated Cool Setpoint"
    state.CoolSetpoint = evt.value.toFloat()
    state.roomSetPoint = state.CoolSetpoint
	debuglog "Cold setpoint set to ${state.CoolSetpoint}"
    childTileUpdate()
}

def tempHandler(evt) {
	infolog "Updated Temperature"
	state.currentTemperature = evt.value.toFloat().round(1)
	debuglog "Room Temperature set to ${state.currentTemperature}"
    childTileUpdate()
}

// Called from child during init
def HVACmode(){
    return state.thermostatMode
}
def HVACstate(){
    return state.operState
}

//**************************************************************
// Called from child when there is an update for the parent
/*
def SetChildStats(RoomStat){
   infolog "Recieved Child Data : ${RoomStat}"
   if(RoomStat.delta==0) RoomStat.delta=(-0.1) //avoids a bunch of divide by zero crap
   if (!state.roomMap[RoomStat.room]) state.roomMap[RoomStat.room]=[:]
   state.roomMap[RoomStat.room].area= RoomStat.area
   state.roomMap[RoomStat.room].delta= RoomStat.delta
   state.childVentSize[RoomStat.room]= RoomStat.area
   state.childDelta[RoomStat.room]= RoomStat.delta
   infolog "Processed Room ${[RoomStat.room]}"
   debuglog "Set Room Data:${[RoomStat.room]}:${state.roomMap[RoomStat.room]}"
   if(!Pause)runIn(10,childVentCalc)
   if(Pause) infolog "HVAC-Pro is Paused, no updates will be sent to rooms."
   debuglog "*** Verify ${[RoomStat.room]}:${[RoomStat.delta]}:${state.roomMap[RoomStat.room].delta} *** ${state.roomMap}"
}
*/
def childUpdateRequest(child){
state.updateFlag=1 
    debuglog "${child} Requested an Update"
}    
    
def getChildData(){ 
 
    childApps.each {child ->
       RoomStat = child.getRoomData()    
       infolog "Retrieved Child Data NEW DATA : ${RoomStat}"
       if(RoomStat.delta==0) RoomStat.delta=(-0.1) //avoids a bunch of divide by zero crap
       if (!state.roomMap[RoomStat.room]) state.roomMap[RoomStat.room]=[:]
       state.roomMap[RoomStat.room].area= RoomStat.area
       state.roomMap[RoomStat.room].delta= RoomStat.delta
       state.childVentSize[RoomStat.room]= RoomStat.area
       state.childDelta[RoomStat.room]= RoomStat.delta
       infolog "Processed Room ${[RoomStat.room]}"
       debuglog "Set Room Data:${[RoomStat.room]}:${state.roomMap[RoomStat.room]}"
    }
       
       if(!Pause)runIn(10,childVentCalc)
       if(Pause) infolog "HVAC-Pro is Paused, no updates will be sent to rooms."
       //debuglog "*** Verify ${[RoomStat.room]}:${[RoomStat.delta]}:${state.roomMap[RoomStat.room].delta} *** ${state.roomMap}"    

       state.updateFlag=0 
       debuglog "Turned off flag"
}




// ================ Do a Bunch of Math ================
def childVentCalc(){
    debuglog "Running Vent Calculations ${state.roomMap}"
    state.areaTotal=0
    state.deltaTotal=0
    state.stage=1
    state.roomMap.each{k, v-> 
        state.areaTotal = state.areaTotal + v.area
        state.deltaTotal = state.deltaTotal + v.delta
    }
      
//Setup initial values for data 
    state.roomMap.each{k, v->  
        v.deltaWGT = (childApps.size() / state.areaTotal * v.area).toFloat()
        v.newDelta = (v.delta / v.deltaWGT).toFloat()
        if (v.delta > maxDelta) v.initArea = v.area       
        if (v.delta < 0) v.initArea = 0
        if ((v.delta >= 0) && (v.delta <= maxDelta)) v.initArea = (v.area/maxDelta*v.delta).toFloat()
    }
    
//Get the initAreaSum and remainder 
   state.initAreaSum=0
   state.roomMap.each{k, v->  
      state.initAreaSum = state.initAreaSum + v.initArea    
      }    
   state.initRem = targetArea - state.initAreaSum
    
//Get the active area
   tempSum=0
   state.roomMap.each{k, v->  
      if (v.delta>0) v.actArea = v.area  
      else v.actArea = 0
      tempSum = tempSum + v.actArea
      }
    state.actAreaSum = tempSum

// InitAreaSum is greater than or Equal to the Target
    if (state.initAreaSum >= targetArea) {
           state.stage=100
           state.actDeltaSum=0
           state.roomMap.each{k, v->  
               if(v.delta>0) v.actDelta = v.newDelta
               else v.actDelta = 0
               
               state.actDeltaSum = state.actDeltaSum + v.actDelta
           }
        
           state.deltaProdSum = 0
           state.roomMap.each{k, v->  
               if(v.actDelta!=0) v.deltaProd = (1/state.actDeltaSum/v.actDelta).toFloat()
               else v.deltaProd = 0
        
               state.deltaProdSum = state.deltaProdSum + v.deltaProd
           }
    
            state.roomMap.each{k, v->  
               if(v.deltaProd>0) v.prodScale = (1/state.deltaProdSum*v.deltaProd).toFloat()
               else v.prodScale = 0
           }
    
// Start the reduction loop.
        state.loopRem=state.initRem
        state.roomMap.each{k, v->  
               v.loopArea = v.initArea
        }
    
        state.count = 0
    while (state.loopRem <(-1) && state.count <100) {
           
            state.loopSum=0
            state.roomMap.each{k, v->  
               v.reduce = (state.loopRem*v.prodScale).toFloat()
               if(v.reduce*(-1) > v.loopArea) v.reduce = (v.loopArea * (-1)).toFloat()
               v.loopArea = (v.loopArea+v.reduce).toFloat()
               state.loopSum = (state.loopSum+v.loopArea) .toFloat()           
            }
        state.loopRem = (targetArea -state.loopSum) .toFloat()
        state.count++
      } 
      state.roomMap.each{k, v->    
          state.childVentSP[k]= Math.round(v.loopArea)//.toInteger()
          state.childVentPCT[k]=(100/v.area*state.childVentSP[k]).toInteger()
      } 
       state.stage=199
   }   //end the if block 
      
// InitAreaSum is Less than Target and actAreaSum is Greater or Equal to Target
   if (state.initAreaSum < targetArea && state.actAreaSum > targetArea) {
        state.stage=200   
       state.actDeltaSum=0
           state.roomMap.each{k, v->  
               if(v.delta>0) v.actDelta = v.newDelta
               else v.actDelta = 0
               
               state.actDeltaSum = state.actDeltaSum + v.actDelta
           }
        
            state.roomMap.each{k, v->  
               if(v.actDelta>0) v.prodScale = (1/state.actDeltaSum*v.actDelta).toFloat()
               else v.prodScale = 0
           }
    
//Start the reduction loop
        state.loopRem=state.initRem
        state.roomMap.each{k, v->  
               v.loopArea = v.initArea
        }
    
        state.count = 0
    while (state.loopRem > 0.1 && state.count <500) {
           
            state.loopSum=0
            state.roomMap.each{k, v->  
               v.reduce = (state.loopRem*v.prodScale).toFloat()
               if(v.reduce + v.loopArea > v.area) v.reduce = (v.area - v.loopArea).toFloat()
               v.loopArea = (v.loopArea+v.reduce).toFloat()
               state.loopSum = (state.loopSum+v.loopArea) .toFloat()         
            }
        state.loopRem = (targetArea -state.loopSum) .toFloat()
        state.count++
      }  
          state.roomMap.each{k, v->    
          state.childVentSP[k]= Math.round(v.loopArea)//.toInteger()
          state.childVentPCT[k]=(100/v.area*state.childVentSP[k]).toInteger()
          }
    state.stage=299
    
   }   //end the if block for stage 2       
     
// InitAreaSum is Less than Target and actAreaSum is Less than Target (use inactive vents)
   if (state.actAreaSum < targetArea) {
         state.stage=300   
         state.actDeltaSum=0
         state.roomMap.each{k, v->  
            if(v.delta>0) v.actDelta = 0
            else v.actDelta = (v.newDelta *(-1))
               
         state.actDeltaSum = state.actDeltaSum + v.actDelta
         }
         
         state.deltaProdSum = 0
         state.roomMap.each{k, v->  
            if(v.actDelta!=0) v.deltaProd = (1/state.actDeltaSum/v.actDelta).toFloat()
            else v.deltaProd = 0
        
            state.deltaProdSum = state.deltaProdSum + v.deltaProd
            }
    
         state.roomMap.each{k, v->  
            if(v.deltaProd>0) v.prodScale = (1/state.deltaProdSum*v.deltaProd).toFloat()
            else v.prodScale = 0
            }      
            
//Start the reduction loop
        state.loopRem=(targetArea-state.actAreaSum)
        state.roomMap.each{k, v->  
               v.loopArea = v.initArea
        }
    
    state.count = 0
    while (state.loopRem > 0.1 && state.count <500) {        
            state.loopSum=0
            state.roomMap.each{k, v->  
               v.reduce = (state.loopRem*v.prodScale).toFloat()
               if(v.reduce + v.loopArea > v.area) v.reduce = (v.area - v.loopArea).toFloat()
               if (v.actDelta!=0) v.loopArea = (v.loopArea+v.reduce).toFloat()
               else v.loopArea = v.area
               state.loopSum = (state.loopSum+v.loopArea) .toFloat()         
            }
        state.loopRem = (targetArea -state.loopSum) .toFloat()
        state.count++
      }  
          state.roomMap.each{k, v->    
          state.childVentSP[k]= Math.round(v.loopArea)//.toInteger()
          state.childVentPCT[k]=(100/v.area*state.childVentSP[k]).toInteger()
          }
    state.stage=399
    
   }   //end the if block for stage 3    

   debuglog "Ran Vent Calculations ${state.roomMap}"   
   debuglog "Child Vent Map ${state.childVentSP}"  
   
}// end calc

def update(){
    if (state.operState=="Cooling" && state.stage>99 && !Pause) sendVentUpdate()
    else if (state.operState=="Heating" && state.stage>99 && !Pause) sendVentUpdate()
    else if (state.operState=="Fan Only" && state.stage>99 && !Pause) sendVentUpdate()
    else if (state.blowerRun=="Closed" && state.stage>99 && !Pause) sendVentUpdate()
    
    if (state.updateFlag==1) getChildData()
 
    debuglog "** Cron ${state.stage}**"    
    //runIn(refresh,update)

}

def sendVentUpdate() {   
    childApps.each {child ->
        state.childVentSP.each{room-> 
            if (child.label==room.key){
                child.setArea(room.value)
                debuglog "Sent New Vent Value to: ${child.label}:${room.value}"
            }
        }  
    } 
    infolog "Updated Child Vent Target Area: ${state.childVentSP}"
    debuglog "****** Update Children Vents (Stage ${state.stage})******"
    state.stage=0
    if(HMDT)childTileUpdate()
}

// UPDATE CHILD TILE DEVICE
def childTileUpdate(){
    if(HMDT) {
        state.msg = "Online"
        fan = state.operState
        if(state.operState=="Idle" && state.blowerRun == "Closed") fan="Fan On"
        def roomDevice = getChildDevice("HMDT_${app.id}")
        roomDevice.setValue(state.thermostatMode,state.operState,state.roomSetPoint,state.currentTemperature,state.msg,state.childVentPCT,state.loopSum)
        infolog "Updated Child Device "
        debuglog "Tile Data ${state.thermostatMode},${fan},${state.roomSetPoint},${state.currentTemperature},${state.msg},${state.childVentPCT},${state.loopSum}"
    }
}

// ========== Debug/Logging ==========
def debuglog(statement){   
    if (debugEnable){log.debug(statement)}
}

def infolog(statement){   
    if (infoEnable){log.info(statement)}
}
