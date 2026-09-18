$(function () {
    // 只初始化一次，并把中文消息直接传给它——原先是靠 .dropify-fr / .dropify-event
    // 两个选择器初始化，但页面里没有任何元素带这两个类，那段中文配置从未生效。
    $('.dropify').dropify({
        messages: {
            'default': '点击或拖拽文件到这里',
            'replace': '点击或拖拽文件到这里来替换文件',
            'remove': '移除文件',
            'error': '对不起，你上传的文件太大了'
        }
    });

    var $fileInput = $('#inputfile');
    var $uploadBtn = $('#uploadFileBtn');
    var $uploadName = $('#uploadName');

    loadResources();

    $uploadBtn.prop('disabled', true);
    $fileInput.on('change', function () {
        var files = $fileInput.get(0).files;
        if (files && files.length > 0) {
            // 换文件等于重新开始，目标名跟着新文件走。
            $uploadName.val(files[0].name);
        }
        refreshUploadButton();
    });

    $uploadBtn.on('click', function () {
        var files = $fileInput.get(0).files;
        if (!files || files.length === 0) {
            showMessage('danger', '请先选择要上传的 JAR 文件');
            return;
        }
        // 后端的文件名校验消息是英文的，这里先挡一道并给出中文说明。
        var nameError = validateUploadName($.trim($uploadName.val()));
        if (nameError) {
            showMessage('danger', nameError);
            $uploadName.focus();
            return;
        }
        upload(files[0]);
    });

    $('#c3_body').on('click', 'button.delete', function () {
        var name = $(this).data('name');
        if (!confirm('确定删除 ' + name + ' 吗？正在被任务引用的 JAR 会被拒绝删除。')) {
            return;
        }
        remove($(this).data('path'));
    });

    // 留空表示沿用原文件名，此时交由后端取名。
    function validateUploadName(name) {
        if (name === '') {
            return null;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return '文件名不能包含路径分隔符';
        }
        if (!/\.jar$/i.test(name)) {
            return '文件名必须以 .jar 结尾';
        }
        return null;
    }

    function refreshUploadButton() {
        var files = $fileInput.get(0).files;
        $uploadBtn.prop('disabled', !files || files.length === 0);
    }

    function loadResources() {
        $.post("/jar/list", {}, function (result) {
            renderList(result);
        }).fail(function () {
            showMessage('danger', '读取资源列表失败');
        });
    }

    function upload(file) {
        var formData = new FormData();
        formData.append("file", file);
        formData.append("name", $.trim($uploadName.val()));
        setBusy(true);
        $.ajax({
            url: "/jar/upload",
            type: "POST",
            processData: false,
            contentType: false,
            data: formData,
            success: function (result) {
                report(result, '上传成功');
            },
            error: function () {
                showMessage('danger', '上传请求失败');
            }
        }).always(function () {
            setBusy(false);
        });
    }

    // 几百 MB 的包要传几十秒，期间必须挡住重复提交。
    function setBusy(busy) {
        $uploadBtn.prop('disabled', busy).text(busy ? '上传中…' : '提交');
        if (!busy) {
            refreshUploadButton();
        }
    }

    function remove(path) {
        $.post("/jar/delete", { jar: path }, function (result) {
            report(result, '删除成功');
        }).fail(function () {
            showMessage('danger', '删除请求失败');
        });
    }

    // 成功与失败都要有反馈：此前失败分支什么都不做，页面看起来像没反应。
    // 覆盖了同名文件时用警告色显示后端给出的说明，而不是一句笼统的"上传成功"。
    function report(result, successText) {
        if (result && result.state === "success") {
            var notice = result.data ? result.data.notice : null;
            showMessage(notice ? 'warning' : 'success', notice || successText);
            clearSelection();
        } else {
            showMessage('danger', errorText(result, '操作失败'));
        }
        renderList(result);
    }

    function errorText(result, fallback) {
        return result && result.data ? String(result.data) : fallback;
    }

    function clearSelection() {
        var instance = $.data($fileInput.get(0), 'plugin_dropify');
        if (instance) {
            instance.clearElement();
        } else {
            $fileInput.val('');
        }
        $uploadName.val('');
        refreshUploadButton();
    }

    function renderList(result) {
        if (!result || result.state !== "success") {
            return;
        }
        var data = result.data || {};
        renderDirectory(data.directory);
        renderRows(data.jars || []);
    }

    function renderDirectory(directory) {
        $('#resourceDirectory').text(directory
            ? '当前目录：' + directory
                + '（来自共享配置 lib.home；该目录可能同时包含运维手工维护的文件）'
            : '');
    }

    function renderRows(jars) {
        var $body = $('#c3_body').empty();
        if (jars.length === 0) {
            $body.append($('<tr>').append(
                $('<td>').attr('colspan', 4).addClass('text-muted').text('当前目录下没有 JAR 文件')));
            return;
        }
        for (var i = 0; i < jars.length; i++) {
            $body.append(buildRow(jars[i]));
        }
    }

    // 逐节点构造而不拼字符串：文件名来自文件系统，直接拼进 HTML 会形成注入面。
    function buildRow(jar) {
        var $deleteButton = $('<button>')
            .attr('type', 'button')
            .addClass('btn btn-default delete')
            .data('name', jar.name)
            .data('path', jar.path)
            .append($('<span>').addClass('glyphicon glyphicon-remove'))
            .append(' 删除');
        return $('<tr>')
            .append($('<td>').text(jar.path))
            .append($('<td>').text(jar.sizeText))
            .append($('<td>').text(jar.modifiedTimeText))
            .append($('<td>').append($deleteButton));
    }

    function showMessage(type, text) {
        $('#alertBox')
            .removeClass('alert-success alert-danger alert-info alert-warning')
            .addClass('alert-' + type);
        $('#alertP').text(text);
        $('#alertDiv').show();
    }
});
