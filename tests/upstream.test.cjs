const { test } = require('node:test')
const path = require('node:path')
const manifest = require('./upstream-contract.json')

test('Android login and storage sources have been reviewed against typle-r',
  { skip: !process.env.TYPLE_R && 'TYPLE_R is not set; upstream compatibility is not checked' }, () => {
    const root = path.resolve(process.env.TYPLE_R)
    require(path.join(root, 'scripts/check-contract-upstream.cjs')).checkUpstream(root, manifest)
  })
