import{describe,expect,it,vi}from'vitest';import{loadKakaoMap}from'./KakaoMapLoader'
describe('KakaoMapLoader',()=>{it('returns a typed error when the key is missing',async()=>{vi.stubGlobal('window',{});await expect(loadKakaoMap('')).rejects.toMatchObject({code:'MISSING_KEY'});vi.unstubAllGlobals()})})
