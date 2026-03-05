package com.sanjay.client.service.controller;

import com.sanjay.client.service.client.SocketClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/query")
@Tag(name = "Query API", description = "Send queries to the socket server")
public class QueryController {

    private final SocketClient socketClient;

    public QueryController(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    @PostMapping(consumes = "text/plain", produces = "text/plain")
    @Operation(
            summary = "Send a company query",
            description = "Submit a company short name (column zero of CSV) as plain text. "
                    + "Examples: CompanyA, CompanyB, CompanyC. "
                    + "Do not send sector or numeric values."
    )
    public String sendQuery(
            @RequestBody(
                    description = "Company short name (e.g. CompanyA)",
                    required = true,
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string"))
            )
            @org.springframework.web.bind.annotation.RequestBody String query) {
        return socketClient.sendQuery(query.trim());
    }

    @GetMapping("/balance/{company}")
    @Operation(
            summary = "ISO8583 Balance Enquiry",
            description = "Initiates a balance enquiry using ISO8583 binary message exchange. " +
                    "The client constructs an ISO8583 request (MTI 0100, processing code 310000) " +
                    "and the server responds with an ISO8583 response (MTI 0110) containing " +
                    "the balance in field 54."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "ISO8583 response message",
                    content = @Content(
                            mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary",
                                    description = "Binary ISO8583 response message containing balance information")
                    )
            )
    })

    public String balanceEnquiry(@PathVariable String company) {
        return socketClient.sendBalanceEnquiry(company);
    }
}