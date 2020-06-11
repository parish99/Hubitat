
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
	}
}

//=========================================================================
def installed(){
    initialize()
}
   
def updated(){
   unsubscribe()
   initialize()
}
   
def initialize(){
    state.clear()
    infolog "Initializing"
    subscribe(tStat, "thermostatMode", setTstatMode)
    debuglog "Getting local virtual Thermostat thermostatMode" 
	state.thermostatMode = tStat.currentValue("thermostatMode").toUpperCase()
    debuglog "Main TstatState : ${state.thermostatMode}"    
    debuglog "Getting Thermostat running State" 
    subscribe(tStat, "thermostatOperatingState", OperatingStateHandler)  
    state.operState = tStat.currentValue("thermostatOperatingState").toUpperCase()
    debuglog "Main TstatState : ${state.operState}"
   
   if (blowerRun) {
   subscribe(blowerRun, "contact", blowerHandler)
   debuglog "Getting Blower State "
   state.blowerRun = blowerRun.currentValue("contact").toUpperCase()
   }
   
   if (staticPres) {
   subscribe(staticPres, "contact", pressureHandler)
   debuglog "Getting Static Pressure State "
   state.overPressure = overPressure.currentValue("contact").toUpperCase()
   }
   
   debuglog "There are ${childApps.size()} installed child apps"
   state.roomMap = [:]

   childApps.each {child ->
      debuglog "Child app: ${child.label}"
      state.roomMap[child.label]=[:]
      state.childVentSize= [:]
      state.childDelta= [:]
      state.childVentSP= [:]
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
    
} //end init

//Get Main Thermostat Mode
def setTstatMode(evt){
	infolog "Running setTstatMode"
	state.thermostatMode = evt.value.toUpperCase()
    debuglog "Sending TStat Change to Zones ${state.thermostatMode}"
	   childApps.each {child -> 
         child.MainTstatModeChange(state.thermostatMode)
       }
}

/*
def getMainTstatState(){
	def TstatState = tStat.currentValue("thermostatOperatingState")
      if (TstatState!=null){TstatState = TstatState.toUpperCase()}
      else {TstatState = "NULL"}
   debuglog "getMainTstatState Main TstatState : ${TstatState}"
	return TstatState
}
*/

def OperatingStateHandler(evt){
	debuglog "OperatingStateHandler event : ${evt}"
	def newTstatState = evt.value.toUpperCase()
      if (newTstatState != state.operState) state.operState = newTstatState
}

def blowerHandler(evt){
    debuglog "Blower Run event : ${evt}"
    state.blowerRun = evt.value.toUpperCase()
    return state.blowerRun
}
   
def pressureHandler(evt){
    debuglog "Overpressure Run event : ${evt}"
    state.overPressure = evt.value.toUpperCase()
    return state.overPressure
}

// Called from child during init
def HVACmode(){
    return state.thermostatMode
}
def HVACstate(){
    return state.operState
}

// Called from child when there is an update for the parent
def SetChildStats(RoomStat){
   infolog "Recieved Child Data : ${RoomStat}"
   if(RoomStat.delta==0) RoomStat.delta=0.1 //avoids a bunch of divide by zero crap
   if (!state.roomMap[RoomStat.room]) state.roomMap[RoomStat.room]=[:]
   state.roomMap[RoomStat.room].area=(RoomStat.area)
   state.roomMap[RoomStat.room].delta=(RoomStat.delta)
   state.childVentSize[RoomStat.room]= RoomStat.area
   state.childDelta[RoomStat.room]= RoomStat.delta
   infolog "Processed Room ${[RoomStat.room]}"
   debuglog "Set Room Data:${[RoomStat.room]}:${state.roomMap[RoomStat.room]}"
   if(!Pause)childVentCalc()
   if(Pause) infolog "HVAC-Pro is Paused, no updates will be sent to rooms."
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

//***********  initAreaSum is greater than or Equal to the Target
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
    
    //Start the reduction loop.
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
      } 
       state.stage=199
   }   //end the if block 
      
//************ initAreaSum is Less than Target and actAreaSum is Greater or Equal to Target
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
          }
    state.stage=299
    
   }   //end the if block for stage 2       
     
//************** initAreaSum is Less than Target and actAreaSum is Less than Target (use inactive vents)
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
          }
    state.stage=399
    
   }   //end the if block for stage 3    

   debuglog "Ran Vent Calculations ${state.roomMap}"   
   debuglog "Child Vent Map ${state.childVentSP}"  
   
}// end calc

def update(){
    if (state.operState=="COOLING" && state.stage>99 && !Pause) sendVentUpdate()
    else if (state.operState=="HEATING" && state.stage>99 && !Pause) sendVentUpdate()
    else if (state.operState=="FAN ONLY" && state.stage>99 && !Pause) sendVentUpdate()
    else if (state.blowerRun=="CLOSED" && state.stage>99 && !Pause) sendVentUpdate()
    runIn(refresh,update)
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
}

//    ========== Debug/Logging ==========
def debuglog(statement){   
    if (debugEnable){log.debug(statement)}
}

def infolog(statement){   
    if (infoEnable){log.info(statement)}
}
 
