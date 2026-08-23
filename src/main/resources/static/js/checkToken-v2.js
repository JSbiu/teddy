(function () {
    $.ajax({
        url: '/teddy/checkToken',
        type: 'POST',
        async: false,
        success: function (result) {
            if (result.state !== 'success') {
                window.location.href = '../../login.html';
            }
        },
        error: function () {
            window.location.href = '../../login.html';
        }
    });
}());
