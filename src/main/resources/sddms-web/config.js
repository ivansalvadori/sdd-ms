addListenerSaveConfig = function() {
	$("#linkSaveConfig").click(function() {
		saveConfig()
	});
}

addListenerExecuteConfig = function() {
	$("#linkExecuteConfig").click(function() {
		executeConfig()
	});
}


var loadConfig = function() {
	$.ajax({
		url : "/sddms/config",
		type : 'GET',
		async : true,
		beforeSend : function(req) {
			req.setRequestHeader("Accept", "text/plain");
		},
		success : function(config) {
			$("#configTextArea").text(config);
		},
		error : function() {

		}
	});
};

var saveConfig = function() {
	$.ajax({
		url : "/sddms/config",
		type : 'POST',
		contentType : "text/plain",
		data : $("#configTextArea").val(),
		async : true,
		success : function() {
			alert("Saved!")
		},
		error : function() {
			alert("Invalid configuration")
		}
	});
};

var executeConfig = function() {
	$.ajax({
		url : "/sddms/reader/read",
		type : 'GET',
		contentType : "text/plain",
		async : true,
		success : function() {
			alert("Setup has been finisehd. SDD-µs is now ready.");
		},
		error : function() {
			alert("Setup has been finisehd. SDD-µs is now ready.");
		}
	});
};

$(document).ready(function() {
	loadConfig();
	addListenerSaveConfig();
	addListenerExecuteConfig();
});