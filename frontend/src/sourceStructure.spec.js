import { describe, expect, it } from 'vitest'

const componentSecurity = import.meta.glob('./components/security/*')
const dataModules = import.meta.glob('./data/*')
const assetStyles = import.meta.glob('./assets/styles/*')
const assetTypes = import.meta.glob('./assets/types/*')
const layoutComponents = import.meta.glob('./components/layout/*.vue')

const legacyModules = {
  layouts: import.meta.glob('./layouts/*'),
  mocks: import.meta.glob('./mocks/*'),
  security: import.meta.glob('./security/*'),
  styles: import.meta.glob('./styles/*'),
  types: import.meta.glob('./types/*'),
}

describe('source folder organization', () => {
  it('groups security modules with components', () => {
    expect(Object.keys(componentSecurity)).toEqual(expect.arrayContaining([
      './components/security/attackPatterns.js',
      './components/security/breachCheck.js',
      './components/security/passwordPolicy.js',
      './components/security/rateLimit.js',
    ]))
  })

  it('groups mock data with the data modules', () => {
    expect(Object.keys(dataModules)).toEqual(expect.arrayContaining([
      './data/courses.ts',
      './data/data.ts',
    ]))
  })

  it('groups global styles and shared types under assets', () => {
    expect(Object.keys(assetStyles)).toEqual(expect.arrayContaining([
      './assets/styles/base.css',
      './assets/styles/fonts.css',
      './assets/styles/tokens.css',
    ]))
    expect(Object.keys(assetTypes)).toEqual(expect.arrayContaining([
      './assets/types/course.ts',
      './assets/types/index.ts',
    ]))
  })

  it('groups layout shells with the layout components', () => {
    expect(Object.keys(layoutComponents)).toEqual(expect.arrayContaining([
      './components/layout/BareLayout.vue',
      './components/layout/DefaultLayout.vue',
      './components/layout/MapLayout.vue',
    ]))
  })

  it('does not leave modules in the superseded source folders', () => {
    for (const modules of Object.values(legacyModules)) {
      expect(Object.keys(modules)).toEqual([])
    }
  })

  it('provides a lazy loader for every relocated module', () => {
    for (const modules of [componentSecurity, dataModules, assetStyles, assetTypes, layoutComponents]) {
      for (const loadModule of Object.values(modules)) {
        expect(loadModule).toBeTypeOf('function')
      }
    }
  })
})
