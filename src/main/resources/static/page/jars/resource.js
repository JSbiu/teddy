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

    loadResources();

    $uploadBtn.prop('disabled', true);
    $fileInput.on('change', refreshUploadButton);

    $uploadBtn.on('click', function () {
        var files = $fileInput.get(0).files;
        if (!files || files.length === 0) {
            showMessage('danger', '请先选择要上传的 JAR 文件');
            return;
        }
        upload(files[0]);
    });

    $('#c3_body').on('click', 'button.delete', function () {
        var name = $(this).data('name');
        if (!confirm('确定删除 ' + name + ' 吗？引用它的任务将无法再提交。')) {
            return;
        }
        remove($(this).data('path'));
    });

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
        });
    }

    function remove(path) {
        $.post("/jar/delete", { jar: path }, function (result) {
            report(result, '删除成功');
        }).fail(function () {
            showMessage('danger', '删除请求失败');
        });
    }

    // 成功与失败都要有反馈：此前失败分支什么都不做，页面看起来像没反应。
    function report(result, successText) {
        if (result && result.state === "success") {
            showMessage('success', successText);
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
            .removeClass('alert-success alert-danger alert-info')
            .addClass('alert-' + type);
        $('#alertP').text(text);
        $('#alertDiv').show();
    }
});
