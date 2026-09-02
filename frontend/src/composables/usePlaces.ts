import{onMounted,ref}from'vue';import{places as fallback}from'../data/data';import{placeService}from'../services/PlaceService';import type{Place}from'../assets/types'
export function usePlaces(){const places=ref<Place[]>(fallback),loading=ref(Boolean(import.meta.env.VITE_TOUR_API_KEY));onMounted(async()=>{places.value=await placeService.getAll();loading.value=false});return{places,loading}}
