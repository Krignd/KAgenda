(function() {
  __BRIDGE_SOURCE__
  // 每次运行保留独立词法作用域，验证器仍可访问脚本内声明的函数。
  window.__kagendaSgValidate = function(__kagendaValidatorName, __kagendaValidatorInput) {
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(__kagendaValidatorName)) throw new Error('Invalid validator');
    switch (__kagendaValidatorName) {
      __VALIDATOR_DISPATCH__
    }
    const __kagendaGlobalValidator = window[__kagendaValidatorName];
    if (typeof __kagendaGlobalValidator !== 'function') throw new Error('Validator unavailable');
    return __kagendaGlobalValidator(__kagendaValidatorInput);
  };
  __ADAPTER_SOURCE__
})();
