plugins {
    id("included-plugin")
}

tasks {
    "sample"(included.SampleTask::class) {
        message.set("Hello included build logic!")
    }
}

