$(function () {
    // 已登录时直接进入任务监控，避免从书签打开登录页还要再输一次账号。
    // 仅为体验优化：鉴权仍由后端拦截器负责，前端检查不作为安全边界。
    $.ajax({
        url: '/teddy/checkToken',
        type: 'POST',
        success: function (result) {
            if (result.state === 'success') {
                window.location.href = './page/taskm/monitor.html';
            }
        }
    });

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
