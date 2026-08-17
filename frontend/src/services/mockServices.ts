import{places}from'../data/data';import type{TravelCondition}from'../assets/types'
const wait=(ms=350)=>new Promise(resolve=>setTimeout(resolve,ms))
export const placeService={async getAll(){await wait(60);return places},async getById(id:string){await wait(60);return places.find(p=>p.id===id)??places[0]}}
export const recommendationService={async create(_condition:TravelCondition){await wait();return{title:'바람 따라, 한적한 동·서부 3일',saving:38,cost:'286,000원',average:27,places}}}
export interface MockUser {email:string;nickname:string}
export const DEMO_CREDENTIALS={email:'demo@hangatjido.com',password:'demo1234'}as const
export const authService={async login(email:string,password:string){await wait(200);if(email!==DEMO_CREDENTIALS.email||password!==DEMO_CREDENTIALS.password)throw new Error('이메일 또는 비밀번호가 올바르지 않습니다.');return{token:'mock-token-hangatjido',user:{email,nickname:'제주바람'}as MockUser}}}
