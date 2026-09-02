import {defineStore} from 'pinia';import{ref}from'vue';import type{TravelCondition}from'../../assets/types'
export const useTravelStore=defineStore('travel',()=>{const condition=ref<TravelCondition>({startDate:'2026-08-13',endDate:'2026-08-15',people:2,budget:500000,regions:['동부','서부'],transportation:'렌터카',styles:['자연','로컬'],preference:'여유롭게'});const saved=ref(false);return{condition,saved}})
