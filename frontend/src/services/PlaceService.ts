import{places as mockPlaces}from'../data/data';import type{Place}from'../assets/types';import{adaptTourPlace}from'./tourApi/TourApiAdapter';import{TourApiService}from'./tourApi/TourApiService'
const tour=new TourApiService();let allPromise:Promise<Place[]>|null=null
async function enrich(place:Place){try{const bundle=await tour.findPlace(place);return bundle?adaptTourPlace(place,bundle):place}catch{return place}}
export const placeService={getAll(){allPromise??=Promise.all(mockPlaces.map(enrich));return allPromise},async getById(id:string){const mock=mockPlaces.find(p=>p.id===id)??mockPlaces[0];return enrich(mock)}}
