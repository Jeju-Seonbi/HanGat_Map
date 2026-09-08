import {defineStore} from 'pinia';import{ref}from'vue';import type{TravelCondition}from'../../assets/types'
import{todayKst,addCalendarDays}from'../../utils/format.js'
// 기본 일정은 오늘부터 2박 3일 - AI 코스 조건 폼(AiCourseView)과 같은 규칙.
// 고정 날짜를 두면 시간이 지난 뒤 메인 퀵스타트에 지나간 일정이 뜬다.
export const useTravelStore=defineStore('travel',()=>{const today=todayKst();const condition=ref<TravelCondition>({startDate:today,endDate:addCalendarDays(today,2),people:2,budget:500000,regions:['동부','서부'],transportation:'렌터카',styles:['자연','로컬'],preference:'여유롭게'});const saved=ref(false);return{condition,saved}})
