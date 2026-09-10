(function () {
    function render() {
        var target = $('#teddy-version');
        if (target.length === 0) {
            return;
        }
        $.ajax({
            url: '/system/health',
            type: 'GET',
            dataType: 'json',
            success: function (result) {
                if (result && result.version) {
                    target.text('v' + result.version);
                }
            }
        });
    }

    $(render);
}());
