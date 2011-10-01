// serve first focussable element
$(document).ready(function() {
  $(".focus").focus();
});

// setup sortable
$(document).ready(function() {
  $(".tasks").sortable({
    update: function() {
      $.post("/reorder", {
        ids: $(this).sortable("toArray")
      });
    }
  });
});

// setup inplace editing
$(document).ready(function() {
  $("li.task a.edit").click(function(event) {
    var li = $(this).parent()[0];
    var id = li.getAttribute("id");
    var text = this.innerHTML;
    li.innerHTML = "<form class='update' action='/update' method='post'>" +
      "<input type='text' name='task' value='" + text + "'>" +
      "<input type='hidden' name='id' value='" + id + "'>" +
      "</form>";
    $(li).find("input[type='text']").focus();
  });
});