set(CLIENT
    client/client.cpp
)
add_executable(challenge main.cpp ${CLIENT})
target_link_libraries(challenge PRIVATE cpr::cpr nlohmann_json::nlohmann_json argparse)
