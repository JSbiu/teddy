// Run with: node tools/test-resource-page.cjs. Executes the actual page script
// with controlled elements and pending requests; no browser or service needed.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

function page() {
    const elements = new Map();
    const requests = [];
    let listCallback;
    function element(key) {
        if (elements.has(key)) return elements.get(key);
        const e = {handlers: {}, props: {}, value: '', content: '', files: [], visible: false};
        e.dropify = () => e;
        e.get = () => e;
        e.on = (events, ...args) => {
            for (const event of events.split(' ')) e.handlers[event] = args[args.length - 1];
            return e;
        };
        e.prop = (key, value) => {
            if (value === undefined) return e.props[key];
            e.props[key] = value; return e;
        };
        e.val = value => {
            if (value === undefined) return e.value;
            e.value = value; if (value === '') e.files = []; return e;
        };
        e.text = value => {
            if (value === undefined) return e.content;
            e.content = value; return e;
        };
        for (const method of ['focus', 'empty', 'append', 'attr', 'data', 'addClass', 'removeClass']) {
            e[method] = () => e;
        }
        e.show = () => { e.visible = true; return e; };
        elements.set(key, e);
        return e;
    }
    function $(value) { return typeof value === 'function' ? value() : element(value); }
    $.trim = value => String(value).trim();
    $.data = () => null;
    $.post = (url, data, success) => {
        assert.equal(url, '/jar/list'); listCallback = success;
        return {fail() { return this; }};
    };
    $.ajax = options => {
        requests.push(options);
        return {always(callback) { options.always = callback; return this; }};
    };
    class FormData { append() {} }
    const source = path.join(__dirname, '../src/main/resources/static/page/jars/resource.js');
    vm.runInNewContext(fs.readFileSync(source, 'utf8'), {$, FormData, confirm: () => true});
    return {element, requests, list: result => listCallback(result), select(name) {
        const input = element('#inputfile'); input.files = [{name}]; input.handlers.change();
    }, click() {
        const button = element('#uploadFileBtn');
        if (!button.props.disabled) button.handlers.click();
    }};
}

const upload = page();
assert.equal(upload.element('#uploadFileBtn').props.disabled, true);
upload.select('first.jar');
upload.click();
assert.equal(upload.requests.length, 1);
assert.equal(upload.element('#uploadFileBtn').props.disabled, true);
assert.equal(upload.element('#inputfile').props.disabled, true);
assert.equal(upload.element('#uploadName').props.disabled, true);
upload.select('second.jar'); // Simulate a change event while the request is pending.
upload.click();
upload.element('#uploadFileBtn').handlers.click(); // A direct event must also be guarded.
assert.equal(upload.element('#uploadFileBtn').props.disabled, true);
assert.equal(upload.requests.length, 1);
upload.requests[0].error();
upload.requests[0].always();
assert.equal(upload.element('#uploadFileBtn').props.disabled, false);
assert.equal(upload.element('#inputfile').props.disabled, false);
assert.equal(upload.element('#uploadName').props.disabled, false);
upload.click();
assert.equal(upload.requests.length, 2);
upload.requests[1].success({state: 'success', data: {jars: []}});
upload.requests[1].always();
assert.equal(upload.element('#uploadFileBtn').props.disabled, true);
assert.equal(upload.element('#inputfile').files.length, 0);

const cleared = page();
cleared.select('clear.jar');
cleared.element('#inputfile').files = [];
cleared.element('#inputfile').handlers['dropify.afterClear']();
assert.equal(cleared.element('#uploadFileBtn').props.disabled, true);

const listing = page();
listing.list({state: 'error', data: 'Unable to read JAR attributes'});
assert.equal(listing.element('#alertDiv').visible, true);
assert.equal(listing.element('#alertP').content, 'Unable to read JAR attributes');
console.log('Resource page regression checks passed (pending upload, retry, completion, clear, list error).');
