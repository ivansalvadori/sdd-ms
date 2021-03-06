var innerResourceCount = 0;

var getApiDoc = function() {
	$
			.ajax({
				url : "/sddms",
				type : 'GET',
				async : true,
				contentType : 'application/json',
				beforeSend : function(req) {
					req.setRequestHeader("Accept", 'application/json');
				},
				success : function(apiDoc) {
					mainGrapgh = apiDoc["@graph"]
					$
							.each(
									mainGrapgh,
									function(index, node) {
										if (node["@type"] == "https://www.w3.org/ns/hydra/core#Class") {
											aSupportedClass = $("<a href='#' class='list-group-item supportedClassItem' seealso='"
													+ node.seeAlso
													+ "'>"
													+ node["@id"] + " </a>")
											$("#listOfSupportedClasses")
													.append(aSupportedClass);

											if (node.inferenceRequired == "true") {
												IRlabel = $("<h6 class='label label-warning' data-toggle='tooltip' title='Inference required!'>IR</h6>");
												$(aSupportedClass).append(
														IRlabel);
											}

											$("#selectSearchClass").append(
													"<option>" + node["@id"]
															+ "</option>");

										}
									});
					addListenerSupportedClassClicked()

					loadOntologyProperties();

				},
				error : function() {

				}
			});
};

addListenerSupportedClassClicked = function() {
	loadingIcon = " <img class='loadingIcon' src='/sddms/loading.gif' width=20/> ";
	$(".supportedClassItem").click(function() {
		$(".loadingIcon").remove();
		$(this).append(loadingIcon);
		url = $(this).attr("seealso");
		loadList(url)
	});
}

addListenerResourceLinkClicked = function() {
	loadingIcon = " <img class='loadingIcon' src='/sddms/loading.gif' width=20/> ";

	$(".resourceLink").off('click');
	$(".resourceLink").click(function() {
		$(".loadingIcon").remove();
		$(this).append(loadingIcon);
		$(this).off('click'); // not working
		url = $(this).attr("resourceuri");
		divtorender = $(this).attr("divtorender");
		loadResource(url, divtorender, 'application/json')
	});
}

loadList = function(url) {
	$("#mainPanel").empty();
	$("#mainPanel").append(
			"Searching for Web resources.... It may take some time.");
	$.ajax({
		url : url,
		type : 'GET',
		async : true,
		contentType : 'application/json',
		beforeSend : function(req) {
			req.setRequestHeader("Accept", "application/ld+json");
		},
		success : function(list) {
			if (!list["items"] && list["next"]) {
				nextPage = list["next"];
				loadList(nextPage);
				return;
			}

			if (!list["items"] && !list["next"]) {
				$("#mainPanel").empty();
				$("#mainPanel").append("Web resource not found.");
				$(".loadingIcon").remove();
				$("#linkNextPage").addClass("hidden");
				return;
			}

			$(".loadingIcon").remove();

			if (list["next"]) {
				$("#linkNextPage").removeClass("hidden");
				$("#linkNextPage").attr("url", list["next"]);
				addListenerLinkPaginationPage();
			} else {
				$("#linkNextPage").addClass("hidden");
			}

			if (list["previous"]) {
				$("#linkPreviousPage").removeClass("hidden");
				$("#linkPreviousPage").attr("url", list["previous"]);
				addListenerLinkPaginationPage();
			} else {
				$("#linkPreviousPage").addClass("hidden");
			}

			items = list["items"]
			$("#mainPanel").empty();

			if (!items) {
				return;
			}

			itemsDiv = $("<div class='panel panel-default id=''>")
			$("#mainPanel").append(itemsDiv);

			if (!Array.isArray(items)) {
				addResourceToList(itemsDiv, items, 0)
			} else {
				itemCount = 0;
				$.each(items, function(index, item) {
					addResourceToList(itemsDiv, item, itemCount)
					itemCount++
				});
			}

			addListenerResourceLinkClicked();
		},
		error : function() {

		}
	});
}

addResourceToList = function(itemsDiv, item, itemCount) {
	itemLabel = item.substring(item.length - 40, item.length)
	itemPanel = '<div class="panel-heading" role="tab" >'
			+ '<h4 class="panel-title">'
			+ '<a class="resourceLink" resourceuri="' + item
			+ '" divtorender="resourceContentDiv_' + itemCount
			+ '" role="button" data-toggle="collapse" href="#' + itemCount
			+ '" aria-expanded="false" aria-controls="' + itemCount + '">'
			+ itemLabel + '</a>' + ' </h4>' + '</div>' + '<div id="'
			+ itemCount + '" class="panel-collapse collapse">'
			+ '<div id="resourceContentDiv_' + itemCount
			+ '" class="panel-body">' + '</div>' + '</div>	'
	$(itemsDiv).append(itemPanel);
}

loadResource = function(url, divtorender, contentType) {
	$("#" + divtorender).empty();
	$
			.ajax({
				url : url,
				type : 'GET',
				async : true,
				contentType : contentType,
				beforeSend : function(req) {
					req
							.setRequestHeader("Accept",
									"text/html;q=0.9, application/ld+json;q=0.8, */*;q=0.8");
				},
				success : function(resource) {
					resourceContent = "";
					if (!resource["@context"]) {
						inserHtmlRepresentation(resource);
					} else {
						$
								.each(
										resource,
										function(key, element) {
											if (key != "@context"
													&& resource["@context"]) {
												contextOfKey = resource["@context"][key];
												if (resource["@context"][key]
														&& contextOfKey["@type"]
														&& contextOfKey["@type"] == "@id") {

													if (!Array.isArray(element)) {
														resourceContent = resourceContent
																+ createLink(
																		key,
																		element)
													} else {
														$
																.each(
																		element,
																		function(
																				index,
																				element) {
																			resourceContent = resourceContent
																					+ createLink(
																							key,
																							element);
																		});
													}

												} else {
													if (element instanceof Object) {
														resourceContent = resourceContent
																+ "<b>"
																+ key
																+ ":</b> "
																+ JSON.stringify(element)
																+ "<br>";
													} else {
														resourceContent = resourceContent
																+ "<b>"
																+ key
																+ ":</b> "
																+ element
																+ "<br>";
													}
												}
											}
										});
						$("#" + divtorender).html(resourceContent)
						addListenerResourceLinkClicked();
						$("#" + divtorender).removeClass("hidden")
						$(".loadingIcon").remove();
					}
				},
				error : function() {
					inserHtmlRepresentation("<b>It was not possible to load this resource</b>")
				}

			});

	loadResourceHtmlRepresentation = function(url) {
		$.ajax({
			url : url,
			type : 'GET',
			async : false,
			contentType : contentType,
			beforeSend : function(req) {
				req.setRequestHeader("Accept", "text/html;q=0.9, */*;q=0.8");
			},
			success : function(resource) {
				$("#" + divtorender).html(resource)
				addListenerResourceLinkClicked();
				$("#" + divtorender).removeClass("hidden")
				$(".loadingIcon").remove();
			},
			error : function(resource) {
				$("#" + divtorender).html(resource)
				addListenerResourceLinkClicked();
				$("#" + divtorender).removeClass("hidden")
				$(".loadingIcon").remove();
			}

		});
	}

	inserHtmlRepresentation = function(resource) {
		$("#" + divtorender).html(resource)
		addListenerResourceLinkClicked();
		$("#" + divtorender).removeClass("hidden")
		$(".loadingIcon").remove();
	}

	createLink = function(key, element) {
		resourceContent = "<b>"
				+ key
				+ ":</b> <a href='#innerResource_"
				+ innerResourceCount
				+ "' class='resourceLink' style='text-decoration: none' resourceuri='"
				+ element + "' divtorender='innerResource_"
				+ innerResourceCount + "'>click to open</a> <br>";
		resourceContent = resourceContent
				+ "<div class='well well-sm hidden' id='innerResource_"
				+ innerResourceCount + "'></div>";
		innerResourceCount++;

		return resourceContent;
	}

}

loadOntologyProperties = function(propertyName) {
	$
			.ajax({
				url : "/sddms/ontology",
				type : 'GET',
				async : true,
				beforeSend : function(req) {
					req.setRequestHeader("Accept", "application/ld+json");
				},
				success : function(ontology) {
					nodos = ontology["@graph"];
					$ontology = ontology;
					propCount = 0;
					$
							.each(
									nodos,
									function(key, element) {
										if (element["@type"] == "owl:DatatypeProperty") {
											var html = '<div class="form-group col-xs-12">'
													+ '<div class="input-group">	'
													+ '<div class="input-group-addon"> <span class="ontologyPropertyId" style="font-size:10px;" for="inputTextOntologyProperty'
													+ propCount
													+ '">'
													+ element["@id"]
													+ '</span> </div> <input type="text" class="form-control" id="inputTextOntologyProperty'
													+ propCount
													+ '">'
													+ '</div>' + '</div>';
											propCount++;
											$("#divOntologyDataPropertyList")
													.append(html)
										}
										if (element["@type"] == "owl:ObjectProperty") {
											var html = '<div class="form-group col-xs-12">'
													+ '<div class="input-group">	'
													+ '<div class="input-group-addon"> <span class="ontologyPropertyId" style="font-size:10px;" for="inputTextOntologyProperty'
													+ propCount
													+ '">'
													+ element["@id"]
													+ '</span> </div> <input type="text" class="form-control" id="inputTextOntologyProperty'
													+ propCount
													+ '">'
													+ '</div>' + '</div>';
											propCount++;
											$("#divOntologyObjectPropertyList")
													.append(html)
										}
									});
					addListenerLinkDoSearch();

				},
				error : function() {

				}
			});

}

addListenerLinkDoSearch = function() {
	loadingIcon = " <img class='loadingIcon' src='/sddms/loading.gif' width=20/ style='border-style: none;'> ";

	$("#linkDoSearch").click(
			function() {
				$(".loadingIcon").remove();
				$(this).append(loadingIcon);

				selectedClass = $("#selectSearchClass").val();
				url = "/sddms/resources?uriClass=" + selectedClass;

				$(".ontologyPropertyId").each(
						function(index, element) {
							associatedInputId = $(this).attr("for");
							propertyValue = $("#" + associatedInputId).val();
							if (propertyValue != "") {
								propertyLable = $(element).html();

								contextPrefixes = Object
										.keys($ontology["@context"]);
								$.each(contextPrefixes, function(key, prefix) {
									if (propertyLable.startsWith(prefix)) {
										propertyLable = propertyLable.replace(
												prefix + ":",
												$ontology["@context"][prefix])
									}
								});

								url = url + "&" + propertyLable + "="
										+ propertyValue
							}
						});

				console.log(url)

				loadList(url)
			});
}

addListenerLinkPaginationPage = function() {
	loadingIcon = " <img class='loadingIcon' src='/sddms/loading.gif' width=20/ style='border-style: none;'> ";

	$("#linkNextPage").off('click');
	$("#linkNextPage").click(function() {
		$(".loadingIcon").remove();
		$(this).append(loadingIcon);
		url = $(this).attr("url");
		loadList(url)
	});

	$("#linkPreviousPage").off('click');
	$("#linkPreviousPage").click(function() {
		$(".loadingIcon").remove();
		$(this).append(loadingIcon);
		url = $(this).attr("url");
		loadList(url)
	});
}

$(document).ready(function() {
	$('[data-toggle="tooltip"]').tooltip();
	var $ontology = "";
	getApiDoc();

});