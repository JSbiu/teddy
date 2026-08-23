$(function () {
    $('#login-form').submit(function (event) {
        event.preventDefault();
        $('#login-error').text('');

        $.ajax({
            url: '/teddy/login',
            type: 'POST',
            data: {
                userName: $('#user-name').val(),
                password: $('#password').val()
            },
            success: function (result) {
                if (result.state === 'success') {
                    window.location.href = './page/taskm/monitor.html';
                    return;
                }
                $('#login-error').text('用户名或密码错误');
            },
            error: function () {
                $('#login-error').text('登录请求失败');
            }
        });
    });
});
