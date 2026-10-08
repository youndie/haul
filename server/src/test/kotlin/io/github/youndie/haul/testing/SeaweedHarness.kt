package io.github.youndie.haul.testing

import io.github.youndie.haul.feature.catalog.data.S3Config
import io.github.youndie.haul.feature.catalog.data.S3PhotoStore
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * One SeaweedFS S3 gateway for the whole suite, the S3 server the portfolio tests against since MinIO's
 * images were withdrawn; each [bucket] is a new, empty bucket in it.
 *
 * The gateway is started **with an identity**, so it checks every signature: without one SeaweedFS
 * lets anonymous requests through, and a signer that signs wrongly would pass. `S3PhotoStoreTest`'s
 * wrong-secret test is the control that it does check.
 */
internal object SeaweedHarness {
    // chrislusf/seaweedfs:4.48, pinned by digest as s3kn pins it (its M-130).
    private const val IMAGE =
        "chrislusf/seaweedfs@sha256:4e61d15fd35994cb1e43e1e553dff106794841fd9a99ade2fc8c8bfce4d7872d"
    private const val PORT = 9000
    const val ACCESS_KEY = "haul-test-access-key"
    const val SECRET_KEY = "haul-test-secret-key"
    private val counter = AtomicInteger()

    private val container: GenericContainer<Nothing> =
        GenericContainer<Nothing>(DockerImageName.parse(IMAGE)).apply {
            withCommand("server", "-dir=/data", "-s3", "-s3.port=$PORT", "-s3.config=/etc/haul/s3.json")
            withCopyToContainer(
                Transferable.of(
                    """{"identities": [{"name": "haul", "credentials": [{"accessKey": "$ACCESS_KEY", """ +
                        """"secretKey": "$SECRET_KEY"}], "actions": ["Admin", "Read", "Write", "List"]}]}""",
                ),
                "/etc/haul/s3.json",
            )
            withExposedPorts(PORT)
            // Answered without credentials, unlike `/`.
            waitingFor(Wait.forHttp("/healthz").forPort(PORT))
            start()
        }

    /** A new, empty bucket, created by SeaweedFS's own shell; the store under test has no CreateBucket. */
    fun bucket(secretKey: String = SECRET_KEY): S3Config {
        val name = "photos-${counter.incrementAndGet()}"
        val created =
            container.execInContainer(
                "sh",
                "-c",
                "echo 's3.bucket.create -name $name' | weed shell -master=localhost:9333",
            )
        check(created.exitCode == 0) { "bucket $name not created: ${created.stderr}" }
        return S3Config(
            endpoint = "http://${container.host}:${container.getMappedPort(PORT)}",
            bucket = name,
            accessKey = ACCESS_KEY,
            secretKey = secretKey,
        )
    }

    fun store(config: S3Config = bucket()): S3PhotoStore = S3PhotoStore(config, now = ::wallClock)

    @Suppress(
        "ktlint:kapkan:wall-clock",
        "The S3 server checks a signature's date against its own clock, so the test signs with the real one.",
    )
    private fun wallClock(): Instant = Instant.now()
}
