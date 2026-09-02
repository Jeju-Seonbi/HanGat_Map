import{describe,expect,it}from'vitest'
import{authService,DEMO_CREDENTIALS,placeService,recommendationService}from'./mockServices'

describe('Mock service layer',()=>{
  it('returns navigable place data',async()=>{const places=await placeService.getAll();expect(places.length).toBeGreaterThanOrEqual(4);expect(places[0].image).toMatch(/^\/images\//)})
  it('creates a lower-congestion recommendation',async()=>{const result=await recommendationService.create({startDate:'2026-08-13',endDate:'2026-08-15',people:2,budget:500000,regions:['동부'],transportation:'렌터카',styles:['자연'],preference:'여유롭게'});expect(result.average).toBeLessThan(31);expect(result.saving).toBe(38)})
  it('authenticates only the documented demo account',async()=>{const auth=await authService.login(DEMO_CREDENTIALS.email,DEMO_CREDENTIALS.password);expect(auth.token).toBeTruthy();expect(auth.user.email).toBe(DEMO_CREDENTIALS.email);await expect(authService.login('wrong@example.com','wrong1234')).rejects.toThrow('올바르지 않습니다')})
})
