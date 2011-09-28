$(document).ready(function() {
  $(".focus").focus()
})

$(document).ready(function() {
  $(".tasks").sortable({
    update: function() {
      $.post("/reorder", {
        ids: $(this).sortable("toArray")
      })
    }
  })
})
