$(function () {
    var editingId = null;
    var cachedList = [];

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function resetForm() {
        editingId = null;
        $('#c4NameInput').val('');
        $('#c4WebhookInput').val('');
        $('#c4IsDefaultInput').prop('checked', false);
        $('#c4_form_title').text('新增通知配置');
    }

    function loadList() {
        $.get('/system/notify-config/list', function (response) {
            var body = $('#c4_body');
            body.empty();
            if (response.state !== 'success') {
                alert(response.data);
                return;
            }
            cachedList = response.data || [];
            if (cachedList.length === 0) {
                body.append("<tr><td colspan='5'>尚未配置任何通知</td></tr>");
                return;
            }
            var html = "";
            for (var i = 0; i < cachedList.length; i++) {
                var item = cachedList[i];
                html += "<tr id='" + item.id + "'>";
                html += "<td>" + escapeHtml(item.name) + "</td>";
                html += "<td style='word-break:break-all;'>" + escapeHtml(item.webhook) + "</td>";
                html += "<td>" + (Number(item.isDefault) === 1 ? '默认' : '') + "</td>";
                html += "<td><button type='button' class='btn btn-default edit'>编辑</button></td>";
                html += "<td><button type='button' class='btn btn-default delete'>删除</button></td>";
                html += "</tr>";
            }
            body.html(html);
        });
    }

    $('#c4_body tr td button.edit').live("click", function () {
        var id = $(this).closest('tr').attr('id');
        for (var i = 0; i < cachedList.length; i++) {
            if (String(cachedList[i].id) === String(id)) {
                editingId = cachedList[i].id;
                $('#c4NameInput').val(cachedList[i].name);
                $('#c4WebhookInput').val(cachedList[i].webhook);
                $('#c4IsDefaultInput').prop('checked', Number(cachedList[i].isDefault) === 1);
                $('#c4_form_title').text('编辑通知配置');
                return;
            }
        }
    });

    $('#c4_body tr td button.delete').live("click", function () {
        var id = $(this).closest('tr').attr('id');
        if (!confirm('确认删除这条通知配置？')) {
            return;
        }
        $.ajax({
            url: "/system/notify-config/delete",
            type: "POST",
            data: {id: id},
            success: function (result) {
                if (result.state === 'success') {
                    if (String(editingId) === String(id)) {
                        resetForm();
                    }
                    loadList();
                    return;
                }
                alert(result.data);
            }
        });
    });

    $('#c4SaveBtn').click(function () {
        var payload = {
            name: $('#c4NameInput').val(),
            webhook: $('#c4WebhookInput').val(),
            isDefault: $('#c4IsDefaultInput').is(':checked') ? 1 : 0
        };
        if (editingId !== null) {
            payload.id = editingId;
        }
        $.ajax({
            url: "/system/notify-config/save",
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify(payload),
            success: function (result) {
                if (result.state === 'success') {
                    resetForm();
                    loadList();
                    return;
                }
                alert(result.data);
            }
        });
    });

    $('#c4ResetBtn').click(function () {
        resetForm();
    });

    loadList();
});
