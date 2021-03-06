package com.searchcode.app;

import com.searchcode.app.service.route.CodeRouteService;
import com.searchcode.app.util.JsonTransformer;
import spark.ModelAndView;
import spark.template.freemarker.FreeMarkerEngine;

import static spark.Spark.get;

/**
 * Provides all the routes for the searchcode.com version of searchcode
 */
public class SearchcodeRoutes {
    public static void RegisterSearchcodeRoutes() {
        get("/", (request, response) -> {
            var codeRouteService = new CodeRouteService();
            var map = codeRouteService.html(request, response);

            if ((Boolean)map.getOrDefault("isIndex", Boolean.TRUE)) {
                return new FreeMarkerEngine().render(new ModelAndView(map, "index.ftl"));
            }

            return new FreeMarkerEngine().render(new ModelAndView(map, "searchcode_searchresults.ftl"));
        });

        get("/healthcheck/", (request, response) -> new JsonTransformer().render(true));
        get("/health-check/", (request, response) -> new JsonTransformer().render(true));

        get("/file/:codeid/*", (request, response) -> {
            var codeRouteService = new CodeRouteService();
            return new FreeMarkerEngine().render(new ModelAndView(codeRouteService.getCode(request, response), "coderesult.ftl"));
        });

        get("/repository/overview/:reponame/", (request, response) -> {
            var codeRouteService = new CodeRouteService();
            return new FreeMarkerEngine().render(new ModelAndView(codeRouteService.getProject(request, response), "repository_overview.ftl"));
        });
    }
}
