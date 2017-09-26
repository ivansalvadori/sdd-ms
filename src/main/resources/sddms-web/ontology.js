addListenerSaveOntoloy = function(){
	$( "#linkSaveOntology" ).click(function() {
		saveOntology()
	});
}

var loadOntology = function() {
  $.ajax({
    url: "/sddms/ontology",
    type: 'GET',
    async: true,
	beforeSend: function(req) {
    req.setRequestHeader("Accept", "text/turtle");},
    success: function(ontology) {
		$("#ontologyTextArea").text(ontology);
    },
    error: function() {

    }
  });
};

var saveOntology = function() {
  $.ajax({
    url: "/sddms/ontology",
    type: 'POST',
	contentType: "text/turtle",
	data: $("#ontologyTextArea").val(),
    async: true,
	success: function() {
		alert("Saved!")
    },
    error: function() {
    	alert("Invalid ontology")
    }
  });
};




$(document).ready(function() {
  loadOntology()
  addListenerSaveOntoloy()
});