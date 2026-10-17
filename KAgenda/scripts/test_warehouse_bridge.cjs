// Own protocol bridge only; never executes a downloaded school script or contacts a portal.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const messages = [];
const context = { _kagendaWarehouse: { dispatch: raw => messages.push(JSON.parse(raw)) } };
context.window = context;
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../app/src/main/assets/shiguang/bridge.js'), 'utf8').replace('__TOKEN__', '"test-session"'), context);

(async () => {
    const api = context.shiguangBridgePromise;
    const first = api.showPrompt('term', 'choose', '1', 'checkTerm');
    const request = messages.shift();
    assert.equal(request.token, 'test-session');
    assert.match(request.id, /^sg_[0-9]{1,8}$/);
    assert.equal(request.payload.validator, 'checkTerm');
    context.__kagendaSgReply(request.id, true, null);
    assert.equal(await first, null); // Cancellation must not become the text "null".
    const second = api.showSingleSelection('semester', '["A","B"]', 1);
    const selection = messages.shift();
    context.__kagendaSgReply(selection.id, true, 0);
    assert.equal(await second, 0); // First item is a valid non-null index.
    const courses = api.saveImportedCourses('[{"name":"sample"}]');
    const saved = messages.shift();
    assert.equal(saved.action, 'courses');
    assert.equal(saved.payload.data, '[{"name":"sample"}]');
    context.__kagendaSgReply(saved.id, true, true);
    assert.equal(await courses, true);
    const rejected = api.saveComboSchedule('{}');
    const combo = messages.shift();
    context.__kagendaSgReply(combo.id, false, 'unsupported');
    await assert.rejects(rejected, /unsupported/);
    context.AndroidBridge.notifyTaskCompletion();
    assert.equal(messages.shift().action, 'complete');
    assert.equal(context.AndroidBridgePromise, api);
    const bridgeSource = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/shiguang/bridge.js'), 'utf8');
    const runnerSource = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/shiguang/runner.js'), 'utf8');
    for (let run = 0; run < 2; run++) {
        const adapter = 'const repeatedName = 1; const validator = value => value === "valid" ? false : "invalid";';
        vm.runInContext(runnerSource.replace('__BRIDGE_SOURCE__', bridgeSource.replace('__TOKEN__', '"isolated-run"'))
            .replace('__VALIDATOR_DISPATCH__', 'case "validator": return validator(__kagendaValidatorInput);').replace('__ADAPTER_SOURCE__', adapter), context);
        assert.equal(context.__kagendaSgValidate('validator', 'valid'), false);
        assert.equal(context.__kagendaSgValidate('validator', 'wrong'), 'invalid');
    }
    console.log('Verified native bridge request IDs, aliases, cancellation, selection, staged payloads and rejection.');
    console.log('Verified repeat execution and script-local prompt validators.');
})().catch(error => { console.error(error); process.exitCode = 1; });
