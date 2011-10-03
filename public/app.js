// serve first focussable element
$(document).ready(function() {
  $(".focus").focus();
});

// setup sortable
$(document).ready(function() {
  $(".tasks").sortable({
    update: function() {
      $.post("order", {
        ids: $(this).sortable("toArray")
      });
    }
  });
});

// setup inplace editing
$(document).ready(function() {
  var handler = function(event) {
    event.preventDefault();

    var li = $(this).parent()[0];
    var id = li.getAttribute("id");
    var text = this.innerHTML;

    li.innerHTML = "<form class='update' action='update' method='post'><div>" +
      "<input type='text' name='task' value='" + text + "'>" +
      "<input type='hidden' name='id' value='" + id + "'>" +
      "</div></form>";

    $(li).find("input[type='text']").focus();
    $(li).find("form.update").submit(function(event) {
      event.preventDefault();
      $.post("update", $(this).serialize(), function(text) {
        var n = document.createElement("div");
        n.innerHTML = text;
        n = n.children[0];
        li.parentNode.replaceChild(n, li);
        $(n).find("a.update").click(handler);
      });
    });
  };

  $("li.task a.update").click(handler);
});