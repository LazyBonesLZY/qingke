#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

/*
 * 这个 WSL 内核上，bind(0) 自动分到的 127.0.0.1 端口无法被本机连接。
 * 改成绑到 20000–31999 里的明确端口。只动本机 IPv4 监听和源端口。
 */
static unsigned next_port = 20000;

static int take_port(void) {
    unsigned port = 20000 + (next_port++ % 12000);
    return (int)port;
}

static int rewrite_bind(int sockfd, int (*real_bind)(int, const struct sockaddr *, socklen_t)) {
    int flags = fcntl(sockfd, F_GETFL, 0);
    int fdflags = fcntl(sockfd, F_GETFD, 0);
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        return -1;
    }
    int opt = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof opt);
    if (flags >= 0) {
        fcntl(fd, F_SETFL, flags);
    }
    if (fdflags >= 0) {
        fcntl(fd, F_SETFD, fdflags);
    }
    for (int i = 0; i < 64; i++) {
        struct sockaddr_in local;
        memset(&local, 0, sizeof local);
        local.sin_family = AF_INET;
        local.sin_addr.s_addr = htonl(0x7f000001);
        local.sin_port = htons((unsigned short)take_port());
        if (real_bind(fd, (struct sockaddr *)&local, sizeof local) == 0) {
            if (dup2(fd, sockfd) < 0) {
                close(fd);
                return -1;
            }
            close(fd);
            return 0;
        }
    }
    close(fd);
    return -1;
}

static int is_v4_mapped_loopback(const struct in6_addr *addr) {
    static const unsigned char prefix[12] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff};
    return memcmp(addr->s6_addr, prefix, 12) == 0 && addr->s6_addr[12] == 127;
}

int bind(int sockfd, const struct sockaddr *addr, socklen_t addrlen) {
    static int (*real_bind)(int, const struct sockaddr *, socklen_t);
    if (!real_bind) {
        real_bind = dlsym(RTLD_NEXT, "bind");
    }
    if (addr && addr->sa_family == AF_INET && addrlen >= (socklen_t)sizeof(struct sockaddr_in)) {
        const struct sockaddr_in *in = (const struct sockaddr_in *)addr;
        unsigned host = ntohl(in->sin_addr.s_addr);
        if (in->sin_port == 0 && (host == INADDR_ANY || (host >> 24) == 127)) {
            if (rewrite_bind(sockfd, real_bind) == 0) {
                return 0;
            }
        }
    }
    if (addr && addr->sa_family == AF_INET6 && addrlen >= (socklen_t)sizeof(struct sockaddr_in6)) {
        const struct sockaddr_in6 *in6 = (const struct sockaddr_in6 *)addr;
        if (in6->sin6_port == 0 && is_v4_mapped_loopback(&in6->sin6_addr)) {
            if (rewrite_bind(sockfd, real_bind) == 0) {
                return 0;
            }
        }
    }
    return real_bind(sockfd, addr, addrlen);
}
